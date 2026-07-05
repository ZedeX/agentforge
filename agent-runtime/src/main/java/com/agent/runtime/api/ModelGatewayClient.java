package com.agent.runtime.api;

import com.agent.runtime.api.dto.ModelChatChunk;
import com.agent.runtime.api.dto.ModelChatRequest;
import com.agent.runtime.api.dto.ModelChatResponse;

import java.util.stream.Stream;

/**
 * Model gateway client (F6 think phase: generate thought / tool_call / final_answer).
 *
 * <p>Routes to agent-model-gateway module (Chat / StreamChat RPC).
 *
 * <p>Two interfaces:
 * <ul>
 *   <li>{@link #chat(ModelChatRequest)} — structured unary call (T4 primary contract).</li>
 *   <li>{@link #chatStream(ModelChatRequest)} — server streaming returning a {@link Stream} of chunks.</li>
 *   <li>{@link #chat(String)} — legacy simplified interface (kept for ThinkPhase mock path).</li>
 * </ul>
 */
public interface ModelGatewayClient {

    /**
     * Structured chat call. Sends {@code request} to model gateway {@code Chat} RPC
     * and returns parsed {@link ModelChatResponse} (content + tool_calls + token usage).
     *
     * @param request structured chat request (messages / params / scene / tier)
     * @return parsed chat response
     * @throws com.agent.runtime.exception.ModelGatewayUnavailableException on UNAVAILABLE / INTERNAL
     * @throws com.agent.runtime.exception.ModelGatewayTimeoutException on DEADLINE_EXCEEDED
     */
    ModelChatResponse chat(ModelChatRequest request);

    /**
     * Streaming chat call. Sends {@code request} to model gateway {@code StreamChat} RPC
     * and returns a lazy {@link Stream} of {@link ModelChatChunk} (delta / tool_call / finish).
     *
     * <p>Caller must close the stream (typically via try-with-resources) to release gRPC resources.
     *
     * @param request structured chat request (stream flag is implicit)
     * @return lazy stream of chunks
     */
    Stream<ModelChatChunk> chatStream(ModelChatRequest request);

    /**
     * Legacy simplified chat: prompt string → raw model output string.
     *
     * <p>Kept for ThinkPhase mock path and tests; production code should prefer {@link #chat(ModelChatRequest)}.
     *
     * @param prompt input prompt
     * @return model output (thought / tool_call(toolId, args) / final_answer)
     * @deprecated use {@link #chat(ModelChatRequest)} for structured I/O and token usage tracking
     */
    @Deprecated
    String chat(String prompt);
}
