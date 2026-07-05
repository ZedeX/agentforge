package com.agent.runtime.api.impl;

import com.agent.runtime.api.ModelGatewayClient;
import com.agent.runtime.api.dto.ModelChatChunk;
import com.agent.runtime.api.dto.ModelChatRequest;
import com.agent.runtime.api.dto.ModelChatResponse;
import com.agent.runtime.api.dto.ModelMessage;
import com.agent.runtime.api.dto.ModelToolCall;
import com.agent.runtime.api.dto.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.stream.Stream;

/**
 * Fallback {@link ModelGatewayClient} for test/standalone environments (T4, doc 06-runtime §8.1).
 *
 * <p>Activated when {@code runtime.model-gateway-client.enabled=false} (or missing), which is the
 * default in {@code application-test.yml}. Returns deterministic mock responses so that
 * {@code RuntimeApplicationTest} context loads and {@code ThinkPhase} can run without a real gateway.
 *
 * <p>Mock behaviour:
 * <ul>
 *   <li>{@link #chat(ModelChatRequest)}: returns first user message reversed, zero token usage,
 *       cacheHit=true, model="mock-model".</li>
 *   <li>{@link #chatStream(ModelChatRequest)}: emits a single STOP chunk with the same content.</li>
 *   <li>{@link #chat(String)}: returns the legacy mock JSON (preserves T1/T3 ThinkPhase test expectations).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "runtime.model-gateway-client.enabled",
        havingValue = "false", matchIfMissing = true)
public class MockModelGatewayClient implements ModelGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(MockModelGatewayClient.class);

    /** Legacy mock response prefix (kept stable for {@code ModelGatewayClientImplTest} legacy assertions). */
    static final String LEGACY_MOCK_OUTPUT = "mock-model-output";

    private static final String MOCK_MODEL = "mock-model";
    private static final String MOCK_PROVIDER = "mock";

    @Override
    public ModelChatResponse chat(ModelChatRequest request) {
        String content = firstUserContent(request);
        log.debug("MockModelGateway chat: callId={}, contentLen={}",
                request.getCallId(), content.length());
        return new ModelChatResponse(
                request.getCallId(),
                MOCK_MODEL,
                MOCK_PROVIDER,
                content,
                Collections.emptyList(),
                TokenUsage.zero(),
                0L,
                true,
                0);
    }

    @Override
    public Stream<ModelChatChunk> chatStream(ModelChatRequest request) {
        String content = firstUserContent(request);
        log.debug("MockModelGateway chatStream: callId={}, contentLen={}",
                request.getCallId(), content.length());
        return Stream.of(
                new ModelChatChunk(request.getCallId(), content),
                ModelChatChunk.finish(request.getCallId(), ModelChatChunk.FinishReason.STOP));
    }

    @Override
    @Deprecated
    public String chat(String prompt) {
        String summary = summarizePrompt(prompt);
        log.info("调用模型网关 (mock): promptSummary={}", summary);
        return "{\"type\":\"thought\",\"prompt\":\"" + summary + "\",\"output\":\"" + LEGACY_MOCK_OUTPUT + "\"}";
    }

    private String firstUserContent(ModelChatRequest request) {
        for (ModelMessage msg : request.getMessages()) {
            if (ModelMessage.ROLE_USER.equals(msg.getRole())) {
                return msg.getContent();
            }
        }
        return "";
    }

    private String summarizePrompt(String prompt) {
        if (prompt == null) return "null";
        return prompt.length() <= 64 ? prompt : prompt.substring(0, 64) + "...";
    }
}
