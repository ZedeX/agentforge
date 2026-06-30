package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.api.ModelProviderAdapter;
import com.agent.modelgateway.model.AdapterContext;
import com.agent.modelgateway.model.ChatReply;
import org.springframework.stereotype.Component;

/**
 * DeepSeek adapter (doc 02-api §5, ADR-003).
 *
 * <p>Skeleton stage: mock chat returns a canned response echoing the prompt prefix.
 * OpenAI-compatible protocol integration deferred to Plan 07 T7.</p>
 */
@Component
public class DeepSeekAdapterImpl implements ModelProviderAdapter {

    private static final String PROVIDER_CODE = "deepseek";
    private static final String MODEL_NAME = "deepseek-v3";

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
        String content = "[deepseek:" + MODEL_NAME + "] " + prompt.substring(0, Math.min(64, prompt.length()));
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
