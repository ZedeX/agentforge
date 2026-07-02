package com.agent.modelgateway.api;

import agentplatform.model.v1.ChatChunk;
import com.agent.modelgateway.model.AdapterContext;
import com.agent.modelgateway.model.ChatReply;
import reactor.core.publisher.Flux;

/**
 * Unified provider adapter interface (ADR-003 multi-provider decoupling).
 *
 * <p>Each vendor (OpenAI / Anthropic / Gemini / Qwen / Ernie / DeepSeek) implements this
 * interface; business layer must NOT couple to vendor-specific SDKs.</p>
 */
public interface ModelProviderAdapter {

    /**
     * Get provider code (e.g. "openai", "anthropic").
     */
    String getProviderCode();

    /**
     * Synchronous chat call.
     *
     * @param context adapter call context (traceId / scene / timeout)
     * @param prompt  user prompt content
     * @return ChatReply with model output + token usage + latency
     */
    ChatReply chat(AdapterContext context, String prompt);

    /**
     * Streaming chat call (server streaming, Plan 07 T9).
     *
     * <p>Returns a Flux of ChatChunk deltas. The service layer subscribes and forwards
     * each chunk to the gRPC StreamObserver. Caller is responsible for setting call_id
     * on each chunk (adapter only fills delta / finish / tool_call).</p>
     *
     * <p>Default implementation throws UnsupportedOperationException — vendors must
     * override when streaming is supported. Skeleton adapters defer to Plan 07 T4-T6.</p>
     *
     * @param context adapter call context (traceId / scene / timeout)
     * @param prompt  user prompt content
     * @return Flux of ChatChunk deltas (without call_id, set by caller)
     */
    default Flux<ChatChunk> streamChat(AdapterContext context, String prompt) {
        throw new UnsupportedOperationException(
                "streamChat not implemented for provider: " + getProviderCode());
    }

    /**
     * Health check: is the adapter ready to serve requests?
     */
    boolean health();
}
