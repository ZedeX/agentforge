package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.api.ModelProviderAdapter;
import com.agent.modelgateway.model.AdapterContext;
import com.agent.modelgateway.model.ChatReply;
import org.springframework.stereotype.Component;

/**
 * OpenAI adapter (doc 02-api §5, ADR-003).
 *
 * <p>Skeleton stage: mock chat returns a canned response echoing the prompt prefix.
 * Spring AI OpenAiChatModel integration deferred to Plan 07 T4.</p>
 */
@Component
public class OpenAiAdapterImpl implements ModelProviderAdapter {

    private static final String PROVIDER_CODE = "openai";
    private static final String MODEL_NAME = "gpt-4o";

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
        String content = "[openai:" + MODEL_NAME + "] " + prompt.substring(0, Math.min(64, prompt.length()));
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
