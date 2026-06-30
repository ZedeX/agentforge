package com.agent.modelgateway.model;

/**
 * Adapter chat response (doc 02-api §5 ModelProviderAdapter.chat return).
 *
 * <p>Skeleton stage: plain POJO mirroring proto ChatResponse fields. proto mapping deferred.</p>
 */
public class ChatReply {

    private final String providerCode;
    private final String modelName;
    private final String content;
    private final int inputTokens;
    private final int outputTokens;
    private final long latencyMs;
    private final boolean cacheHit;

    public ChatReply(String providerCode, String modelName, String content,
                     int inputTokens, int outputTokens, long latencyMs, boolean cacheHit) {
        this.providerCode = providerCode;
        this.modelName = modelName;
        this.content = content;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.cacheHit = cacheHit;
    }

    public String getProviderCode() { return providerCode; }

    public String getModelName() { return modelName; }

    public String getContent() { return content; }

    public int getInputTokens() { return inputTokens; }

    public int getOutputTokens() { return outputTokens; }

    public long getLatencyMs() { return latencyMs; }

    public boolean isCacheHit() { return cacheHit; }
}
