package com.agent.memory.api.impl;

import com.agent.memory.config.MemoryProperties;
import com.agent.memory.exception.EmbeddingServiceFailureException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T5: HTTP 实现 {@link EmbeddingClientImpl} MockWebServer 单元测试。
 *
 * <p>对齐 Plan 03 T5 Red 测试要求（6 个用例）：
 * <ol>
 *   <li>{@link #embed_single_returns1024DimVector}：单条文本 → float[1024]</li>
 *   <li>{@link #embed_batch_returnsList}：批量 3 条 → List&lt;float[]&gt; size=3</li>
 *   <li>{@link #embed_retryOnTimeout}：504→200，重试 1 次后成功</li>
 *   <li>{@link #embed_throwOnMaxRetryExceeded}：3 次 504 → 抛 EmbeddingServiceFailureException</li>
 *   <li>{@link #embed_sendTenantIdHeader}：请求头包含 X-Tenant-Id</li>
 *   <li>{@link #embed_emptyInput_returnsEmptyList}：空输入 → 空 List（不发 HTTP）</li>
 * </ol>
 *
 * <p>测试策略：用 MockWebServer 模拟 agent-model-gateway /v1/embeddings，
 * 通过包级可见构造器 {@link EmbeddingClientImpl#EmbeddingClientImpl(MemoryProperties, WebClient)}
 * 注入指向 MockWebServer 的 WebClient。
 */
class EmbeddingClientImplTest {

    private MockWebServer server;
    private EmbeddingClientImpl client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        // 构造 MemoryProperties，base-url 指向 MockWebServer
        MemoryProperties props = new MemoryProperties();
        MemoryProperties.Embedding emb = props.getEmbedding();
        emb.setHttpEnabled(true);
        emb.setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
        emb.setPath("/v1/embeddings");
        emb.setModel("text-embedding-v3");
        emb.setApiKey("");
        emb.setConnectTimeoutMs(2000);
        emb.setReadTimeoutMs(3000);
        emb.setMaxRetries(3);
        emb.setRetryBackoffBaseMs(50);  // 缩短退避以加速测试
        emb.setRetryBackoffMultiplier(3.0);
        emb.setCacheEnabled(false);  // 测试关闭缓存避免干扰
        WebClient webClient = WebClient.builder().baseUrl(emb.getBaseUrl()).build();
        client = new EmbeddingClientImpl(props, webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("embed_single_returns1024DimVector: 单条文本应返回 1024 维 float[]")
    void embed_single_returns1024DimVector() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildEmbeddingResponse(1)));

        float[] vector = client.embed("hello world", "tenant-001");

        assertThat(vector).hasSize(1024);
        // 验证实际发出了 HTTP 请求
        assertThat(server.getRequestCount()).isEqualTo(1);
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/embeddings");
        assertThat(request.getMethod()).isEqualTo("POST");
    }

    @Test
    @DisplayName("embed_batch_returnsList: 批量 3 条应返回 List<float[]> size=3，每个 1024 维")
    void embed_batch_returnsList() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildEmbeddingResponse(3)));

        List<float[]> result = client.embedBatch(
                List.of("text-a", "text-b", "text-c"), "tenant-001");

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).hasSize(1024);
        assertThat(result.get(1)).hasSize(1024);
        assertThat(result.get(2)).hasSize(1024);
        // 3 个向量应互不相同
        assertThat(result.get(0)).isNotEqualTo(result.get(1));
        assertThat(server.getRequestCount()).isEqualTo(1);
        // 请求体应包含全部 3 条文本
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("text-a").contains("text-b").contains("text-c");
    }

    @Test
    @DisplayName("embed_retryOnTimeout: 第一次 504 / 第二次 200，应重试 1 次后成功")
    void embed_retryOnTimeout() {
        // 第一次 504（gateway timeout），第二次 200
        server.enqueue(new MockResponse().setResponseCode(504).setBody("Gateway Timeout"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildEmbeddingResponse(1)));

        float[] vector = client.embed("retry-test", "tenant-001");

        assertThat(vector).hasSize(1024);
        // 验证重试了 1 次：共 2 次 HTTP 请求
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("embed_throwOnMaxRetryExceeded: 3 次全 504 应抛 EmbeddingServiceFailureException")
    void embed_throwOnMaxRetryExceeded() {
        // maxRetries=3，全部 504
        server.enqueue(new MockResponse().setResponseCode(504).setBody("Gateway Timeout 1"));
        server.enqueue(new MockResponse().setResponseCode(504).setBody("Gateway Timeout 2"));
        server.enqueue(new MockResponse().setResponseCode(504).setBody("Gateway Timeout 3"));

        assertThatThrownBy(() -> client.embed("fail-test", "tenant-001"))
                .isInstanceOf(EmbeddingServiceFailureException.class)
                .hasMessageContaining("failed after 3 attempts");

        // 验证恰好重试到 3 次（无第 4 次）
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("embed_sendTenantIdHeader: 请求头应包含 X-Tenant-Id")
    void embed_sendTenantIdHeader() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildEmbeddingResponse(1)));

        client.embed("header-test", "tenant-xyz-123");

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("X-Tenant-Id")).isEqualTo("tenant-xyz-123");
        assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");
    }

    @Test
    @DisplayName("embed_emptyInput_returnsEmptyList: 空输入应返回空 List 且不发 HTTP 请求")
    void embed_emptyInput_returnsEmptyList() {
        List<float[]> result = client.embedBatch(List.of(), "tenant-001");

        assertThat(result).isEmpty();
        // 不应发出任何 HTTP 请求（短路返回）
        assertThat(server.getRequestCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("embed_nullText_throwsIllegalArgumentException: null/空文本应拒绝")
    void embed_nullText_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> client.embed(null, "tenant-001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.embed("", "tenant-001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(server.getRequestCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("embed_4xx_doesNotRetry: 4xx 错误应立即失败不重试")
    void embed_4xx_doesNotRetry() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("Bad Request"));

        assertThatThrownBy(() -> client.embed("bad-request-test", "tenant-001"))
                .isInstanceOf(EmbeddingServiceFailureException.class)
                .hasMessageContaining("400");

        // 4xx 不应重试
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("embed_cacheHit_avoidsSecondHttpCall: 缓存命中应避免第二次 HTTP 调用")
    void embed_cacheHit_avoidsSecondHttpCall() {
        // 重新构造开启缓存的 client
        MemoryProperties props = new MemoryProperties();
        MemoryProperties.Embedding emb = props.getEmbedding();
        emb.setHttpEnabled(true);
        emb.setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
        emb.setPath("/v1/embeddings");
        emb.setReadTimeoutMs(3000);
        emb.setMaxRetries(3);
        emb.setRetryBackoffBaseMs(50);
        emb.setRetryBackoffMultiplier(3.0);
        emb.setCacheEnabled(true);
        emb.setCacheTtlMinutes(60);
        emb.setCacheMaxSize(100);
        WebClient webClient = WebClient.builder().baseUrl(emb.getBaseUrl()).build();
        EmbeddingClientImpl cachedClient = new EmbeddingClientImpl(props, webClient);

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildEmbeddingResponse(1)));

        // 第一次调用 → HTTP 请求 + 缓存写入
        float[] v1 = cachedClient.embed("cached-text", "tenant-001");
        assertThat(v1).hasSize(1024);
        assertThat(server.getRequestCount()).isEqualTo(1);

        // 第二次相同文本 → 缓存命中，无 HTTP 请求
        float[] v2 = cachedClient.embed("cached-text", "tenant-001");
        assertThat(v2).containsExactly(v1);
        assertThat(server.getRequestCount()).isEqualTo(1);  // 仍然是 1
    }

    /**
     * 构造 OpenAI 兼容嵌入响应 JSON：count 个 1024 维向量。
     */
    private static String buildEmbeddingResponse(int count) {
        StringBuilder sb = new StringBuilder("{\"data\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"embedding\":[");
            for (int j = 0; j < 1024; j++) {
                if (j > 0) sb.append(',');
                // 不同向量用不同种子以便区分
                sb.append(formatFloat(((i + 1) * 0.013 + (j + 1) * 0.0017) % 1.0));
            }
            sb.append("],\"index\":").append(i).append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String formatFloat(double v) {
        // 截断到 6 位小数避免 JSON 体积过大
        return String.format("%.6f", v);
    }
}
