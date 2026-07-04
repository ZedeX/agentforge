package com.agent.memory.api.impl;

import com.agent.memory.api.EmbeddingClient;
import com.agent.memory.config.MemoryProperties;
import com.agent.memory.embedding.EmbeddingRequestBuilder;
import com.agent.memory.embedding.EmbeddingResponseParser;
import com.agent.memory.exception.EmbeddingServiceFailureException;
import com.agent.memory.model.EmbeddingVector;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * T5: 嵌入向量客户端 HTTP 实现（F12.D5: write-time vectorization）。
 *
 * <p>调用 agent-model-gateway 的 OpenAI 兼容接口 {@code POST /v1/embeddings}，
 * 返回 1024 维 float[] 向量（对齐 doc 04-memory §7.2）。
 *
 * <p>仅在 {@code memory.embedding.http-enabled=true} 时激活（生产环境）。
 * 测试环境使用 {@link MockEmbeddingClientImpl}（{@code http-enabled=false}）。
 *
 * <h3>关键能力</h3>
 * <ul>
 *   <li>WebClient 异步 HTTP 调用 + 同步阻塞返回（{@code .block()}）</li>
 *   <li>3 次重试 + 指数退避（100/300/900 ms，对齐 §12.3）</li>
 *   <li>5xx / 超时重试，4xx 立即失败</li>
 *   <li>连接超时 2s / 读取超时 10s（对齐 §12.3）</li>
 *   <li>Caffeine 本地缓存（key=text, TTL=1h, maxSize=10000，对齐 §7.5）</li>
 *   <li>请求头：{@code Authorization: Bearer <apiKey>} + {@code X-Tenant-Id: <tenantId>}</li>
 * </ul>
 *
 * @see EmbeddingClient
 * @see MockEmbeddingClientImpl
 * @see EmbeddingRequestBuilder
 * @see EmbeddingResponseParser
 */
@Component
@ConditionalOnProperty(name = "memory.embedding.http-enabled", havingValue = "true")
public class EmbeddingClientImpl implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClientImpl.class);

    private final WebClient webClient;
    private final MemoryProperties.Embedding embeddingProps;
    private final EmbeddingRequestBuilder requestBuilder;
    private final EmbeddingResponseParser responseParser;
    private final Cache<String, float[]> cache;

    /**
     * 生产构造器：基于 {@link MemoryProperties} 配置构造 WebClient。
     */
    public EmbeddingClientImpl(MemoryProperties props) {
        this(props, WebClient.builder()
                .baseUrl(props.getEmbedding().getBaseUrl())
                .build());
    }

    /**
     * 测试构造器：注入自定义 WebClient（指向 MockWebServer）。
     */
    EmbeddingClientImpl(MemoryProperties props, WebClient webClient) {
        this.embeddingProps = props.getEmbedding();
        this.webClient = webClient;
        this.requestBuilder = new EmbeddingRequestBuilder(embeddingProps.getModel());
        this.responseParser = new EmbeddingResponseParser();
        this.cache = embeddingProps.isCacheEnabled()
                ? Caffeine.newBuilder()
                        .expireAfterWrite(embeddingProps.getCacheTtlMinutes(), TimeUnit.MINUTES)
                        .maximumSize(embeddingProps.getCacheMaxSize())
                        .build()
                : null;
    }

    @Override
    public EmbeddingVector embed(String text) {
        return new EmbeddingVector(embed(text, null));
    }

    @Override
    public float[] embed(String text, String tenantId) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text must not be null or empty");
        }
        if (cache != null) {
            float[] cached = cache.getIfPresent(text);
            if (cached != null) {
                log.debug("Embedding cache hit textLen={}", text.length());
                return cached;
            }
        }
        List<float[]> result = doEmbedBatch(List.of(text), tenantId);
        float[] vector = result.get(0);
        EmbeddingResponseParser.validateDimension(vector);
        if (cache != null) {
            cache.put(text, vector);
        }
        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts, String tenantId) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        // 缓存分流：命中走缓存，未命中合并请求
        List<float[]> result = new ArrayList<>(texts.size());
        List<Integer> uncachedIndices = new ArrayList<>();
        List<String> uncachedTexts = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (cache != null) {
                float[] cached = cache.getIfPresent(text);
                if (cached != null) {
                    result.add(cached);
                    continue;
                }
            }
            result.add(null);
            uncachedIndices.add(i);
            uncachedTexts.add(text);
        }

        if (uncachedTexts.isEmpty()) {
            log.debug("Embedding batch all cache hit size={}", texts.size());
            return result;
        }

        List<float[]> fetched = doEmbedBatch(uncachedTexts, tenantId);
        for (int j = 0; j < fetched.size(); j++) {
            int origIdx = uncachedIndices.get(j);
            float[] vector = fetched.get(j);
            EmbeddingResponseParser.validateDimension(vector);
            result.set(origIdx, vector);
            if (cache != null) {
                cache.put(uncachedTexts.get(j), vector);
            }
        }
        return result;
    }

    /**
     * 实际 HTTP 调用 + 重试逻辑。
     *
     * <p>重试策略：
     * <ul>
     *   <li>5xx 错误 / 超时 / 网络错误 → 重试（最多 maxRetries 次总尝试）</li>
     *   <li>4xx 错误 → 立即失败（请求格式错误，重试无意义）</li>
     *   <li>解析错误 → 立即失败（响应格式异常）</li>
     * </ul>
     */
    private List<float[]> doEmbedBatch(List<String> texts, String tenantId) {
        int maxAttempts = embeddingProps.getMaxRetries();
        Map<String, Object> requestBody = requestBuilder.build(texts);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String responseBody = webClient.post()
                        .uri(embeddingProps.getPath())
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .header("X-Tenant-Id", tenantId == null ? "" : tenantId)
                        .headers(headers -> {
                            String apiKey = embeddingProps.getApiKey();
                            if (apiKey != null && !apiKey.isEmpty()) {
                                headers.setBearerAuth(apiKey);
                            }
                        })
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofMillis(embeddingProps.getReadTimeoutMs()))
                        .block();

                List<float[]> vectors = responseParser.parse(responseBody);
                if (vectors.size() != texts.size()) {
                    throw new EmbeddingServiceFailureException(
                            "Embedding count mismatch: expected=" + texts.size()
                                    + ", actual=" + vectors.size());
                }
                if (attempt > 1) {
                    log.info("Embedding call succeeded on attempt {}/{} textsSize={}",
                            attempt, maxAttempts, texts.size());
                }
                return vectors;
            } catch (EmbeddingServiceFailureException e) {
                // 解析错误 / 维度不符 / 数量不符 → 立即失败，不重试
                throw e;
            } catch (WebClientResponseException e) {
                // 4xx 不重试，5xx 重试
                if (!e.getStatusCode().is5xxServerError()) {
                    throw new EmbeddingServiceFailureException(
                            "Embedding call failed with status " + e.getStatusCode().value()
                                    + ": " + e.getResponseBodyAsString(), e);
                }
                handleRetryableFailure(attempt, maxAttempts, "HTTP " + e.getStatusCode().value(), e, texts.size());
            } catch (Exception e) {
                // 超时 / 网络错误 / 其他 → 重试
                handleRetryableFailure(attempt, maxAttempts, e.getClass().getSimpleName(), e, texts.size());
            }
        }
        // 不可达：循环内必然 return 或 throw
        throw new EmbeddingServiceFailureException("Unreachable: retry loop exhausted");
    }

    private void handleRetryableFailure(int attempt, int maxAttempts, String reason,
                                        Exception e, int textsSize) {
        if (attempt >= maxAttempts) {
            log.error("Embedding call failed after {} attempts reason={} textsSize={}",
                    maxAttempts, reason, textsSize, e);
            throw new EmbeddingServiceFailureException(
                    "Embedding call failed after " + maxAttempts + " attempts (last reason: " + reason + ")", e);
        }
        long backoffMs = computeBackoff(attempt);
        log.warn("Embedding attempt {}/{} failed reason={}, retry in {}ms",
                attempt, maxAttempts, reason, backoffMs);
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new EmbeddingServiceFailureException("Interrupted during retry backoff", ie);
        }
    }

    /**
     * 指数退避：base * multiplier^(attempt-1)。
     *
     * <p>对齐 §12.3：100/300/900 ms（attempt=1,2,3）。
     */
    private long computeBackoff(int attempt) {
        double backoff = embeddingProps.getRetryBackoffBaseMs()
                * Math.pow(embeddingProps.getRetryBackoffMultiplier(), attempt - 1);
        return (long) backoff;
    }
}
