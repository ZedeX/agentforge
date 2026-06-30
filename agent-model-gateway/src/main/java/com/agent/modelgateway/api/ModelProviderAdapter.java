package com.agent.modelgateway.api;

import com.agent.modelgateway.model.AdapterContext;
import com.agent.modelgateway.model.ChatReply;

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
     * Health check: is the adapter ready to serve requests?
     */
    boolean health();
}
