package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.api.ModelProviderAdapter;
import com.agent.modelgateway.model.AdapterContext;
import com.agent.modelgateway.model.ChatReply;
import org.springframework.stereotype.Component;

/**
 * Anthropic adapter (doc 02-api §5, ADR-003).
 *
 * <p>Skeleton stage: mock chat returns a canned response echoing the prompt prefix.
 * Spring AI AnthropicChatModel integration deferred to Plan 07 T5.</p>
 */
@Component
public class AnthropicAdapterImpl implements ModelProviderAdapter {

    private static final String PROVIDER_CODE = "anthropic";
    private static final String MODEL_NAME = "claude-opus-4";

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public ChatReply chat(AdapterContext context, String prompt) {
        if (prompt == null) {
            prompt = "";
        }
        long start = System.currentTimeMillis();
        // Mock: echo first 64 chars of prompt as the response
        String content = "[anthropic:" + MODEL_NAME + "] " + prompt.substring(0, Math.min(64, prompt.length()));
        long latency = System.currentTimeMillis() - start;
        int inputTokens = Math.max(1, prompt.length() / 4);
        int outputTokens = Math.max(1, content.length() / 4);
        return new ChatReply(PROVIDER_CODE, MODEL_NAME, content, inputTokens, outputTokens, latency, false);
    }

    @Override
    public boolean health() {
        return true;
    }
}
