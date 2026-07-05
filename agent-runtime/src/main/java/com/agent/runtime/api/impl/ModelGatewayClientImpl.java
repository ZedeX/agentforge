package com.agent.runtime.api.impl;

import agentplatform.model.v1.ChatChunk;
import agentplatform.model.v1.ChatRequest;
import agentplatform.model.v1.ChatResponse;
import agentplatform.model.v1.ModelGatewayGrpc;
import com.agent.runtime.api.ModelGatewayClient;
import com.agent.runtime.api.dto.ModelChatChunk;
import com.agent.runtime.api.dto.ModelChatRequest;
import com.agent.runtime.api.dto.ModelChatResponse;
import com.agent.runtime.circuit.ResilienceDecorator;
import com.agent.runtime.exception.ModelGatewayTimeoutException;
import com.agent.runtime.exception.ModelGatewayUnavailableException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Real model gateway client implementation backed by gRPC stubs (T4, doc 06-runtime §3).
 * T9: Resilience4j circuit breaker + retry decoration applied.
 *
 * <p>Activated only when {@code runtime.model-gateway-client.enabled=true}. In test/standalone
 * environments where no real gateway is reachable, {@link MockModelGatewayClient} takes over.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Map DTO {@link ModelChatRequest} → proto {@link ChatRequest} via {@link ChatCompletionMapper}.</li>
 *   <li>Call {@code ModelGatewayBlockingStub.chat} with deadline = {@code request.timeoutMs}.</li>
 *   <li>For {@link #chatStream(ModelChatRequest)}, iterate {@code StreamChat} blocking iterator.</li>
 *   <li>Translate gRPC {@link StatusRuntimeException} → {@link ModelGatewayUnavailableException}
 *       / {@link ModelGatewayTimeoutException}.</li>
 *   <li>Legacy {@link #chat(String)} delegates to {@link #chat(ModelChatRequest)} with single user message.</li>
 *   <li>Circuit breaker: {@code model-gateway} (5 failures → open 30s).</li>
 *   <li>Retry: 3 attempts with exponential backoff (200/600/1800 ms).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "runtime.model-gateway-client.enabled", havingValue = "true")
public class ModelGatewayClientImpl implements ModelGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(ModelGatewayClientImpl.class);

    private final ModelGatewayGrpc.ModelGatewayBlockingStub blockingStub;
    private final ChatCompletionMapper mapper;
    private final ResilienceDecorator resilience;

    public ModelGatewayClientImpl(ModelGatewayGrpc.ModelGatewayBlockingStub blockingStub,
                                  ResilienceDecorator resilience) {
        this.blockingStub = blockingStub;
        this.mapper = new ChatCompletionMapper();
        this.resilience = resilience;
    }

    @Override
    public ModelChatResponse chat(ModelChatRequest request) {
        ChatRequest proto = mapper.toProto(request);
        log.debug("ModelGateway chat: callId={}, scene={}, tier={}, timeoutMs={}",
                request.getCallId(), request.getScene(), request.getTier(), request.getTimeoutMs());

        ModelChatResponse resp = resilience.decorateModelGateway(() -> {
            ChatResponse r;
            try {
                r = blockingStub.withDeadlineAfter(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
                        .chat(proto);
            } catch (StatusRuntimeException ex) {
                throw translateException(ex, request.getCallId());
            }
            log.debug("ModelGateway chat done: callId={}, model={}, tokens={}",
                    r.getCallId(), r.getModel(), r.getInputTokens() + r.getOutputTokens());
            return mapper.fromProto(r);
        }).get();
        return resp;
    }

    @Override
    public Stream<ModelChatChunk> chatStream(ModelChatRequest request) {
        ChatRequest proto = mapper.toProto(request);
        log.debug("ModelGateway chatStream: callId={}, scene={}",
                request.getCallId(), request.getScene());

        Iterator<ChatChunk> it;
        try {
            it = blockingStub.withDeadlineAfter(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
                    .streamChat(proto);
        } catch (StatusRuntimeException ex) {
            throw translateException(ex, request.getCallId());
        }

        // Wrap iterator as Stream; close handler triggered by Stream.iterator() exhaustion.
        return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(it, 0), false)
                .map(mapper::fromProto)
                .onClose(() -> {
                    while (it.hasNext()) {
                        it.next(); // drain to release server-side resources on cancellation
                    }
                });
    }

    @Override
    @Deprecated
    public String chat(String prompt) {
        ModelChatRequest req = ModelChatRequest.builder()
                .callId("legacy-" + System.nanoTime())
                .scene("intent")
                .tier("middle")
                .userMessage(prompt)
                .temperature(0.7)
                .maxTokens(1024)
                .timeoutMs(30_000L)
                .build();
        ModelChatResponse resp = chat(req);
        return resp.getContent();
    }

    /** Translate gRPC status → typed model gateway exception (T4 §12.4 error mapping). */
    private RuntimeException translateException(StatusRuntimeException ex, String callId) {
        Status.Code code = ex.getStatus().getCode();
        String desc = ex.getStatus().getDescription();
        log.warn("ModelGateway RPC failed: callId={}, code={}, desc={}", callId, code, desc);

        if (code == Status.Code.DEADLINE_EXCEEDED) {
            return new ModelGatewayTimeoutException(
                    "model gateway timeout: callId=" + callId, ex);
        }
        // UNAVAILABLE / INTERNAL / UNKNOWN / others → unavailable
        return new ModelGatewayUnavailableException(
                "model gateway unavailable: code=" + code + ", callId=" + callId, ex);
    }
}
