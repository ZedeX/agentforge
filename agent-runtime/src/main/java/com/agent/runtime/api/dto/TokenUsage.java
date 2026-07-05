package com.agent.runtime.api.dto;

/**
 * Token usage stats DTO (T4, doc 06-runtime §3).
 *
 * <p>Aggregates prompt / completion / total token counts returned by model gateway.
 * Maps to {@code ChatResponse.input_tokens + output_tokens}.
 */
public class TokenUsage {

    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;

    public TokenUsage(int promptTokens, int completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
    }

    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }

    public TokenUsage add(TokenUsage other) {
        if (other == null) return this;
        return new TokenUsage(
                this.promptTokens + other.promptTokens,
                this.completionTokens + other.completionTokens);
    }

    public static TokenUsage zero() { return new TokenUsage(0, 0); }
}
