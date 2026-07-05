package com.agent.runtime.api.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured chat response DTO (T4, doc 06-runtime §3).
 *
 * <p>Maps from {@code agentplatform.model.v1.ChatResponse}: content + tool_calls + token usage + cost.
 * Returned by {@code ModelGatewayClient.chat(ModelChatRequest)}.
 */
public class ModelChatResponse {

    private final String callId;
    private final String model;
    private final String provider;
    private final String content;
    private final List<ModelToolCall> toolCalls;
    private final TokenUsage tokenUsage;
    private final long costCent;
    private final boolean cacheHit;
    private final int durationMs;

    public ModelChatResponse(String callId, String model, String provider, String content,
                             List<ModelToolCall> toolCalls, TokenUsage tokenUsage,
                             long costCent, boolean cacheHit, int durationMs) {
        this.callId = callId;
        this.model = model;
        this.provider = provider;
        this.content = content;
        this.toolCalls = toolCalls == null ? Collections.emptyList() : new ArrayList<>(toolCalls);
        this.tokenUsage = tokenUsage == null ? TokenUsage.zero() : tokenUsage;
        this.costCent = costCent;
        this.cacheHit = cacheHit;
        this.durationMs = durationMs;
    }

    /** Whether the model emitted any tool calls (ThinkPhase should treat as Act, not final answer). */
    public boolean hasToolCalls() { return !toolCalls.isEmpty(); }

    /** First tool call, or null if none. Convenience for single-tool-call scenarios. */
    public ModelToolCall firstToolCall() {
        return toolCalls.isEmpty() ? null : toolCalls.get(0);
    }

    public String getCallId() { return callId; }
    public String getModel() { return model; }
    public String getProvider() { return provider; }
    public String getContent() { return content; }
    public List<ModelToolCall> getToolCalls() { return Collections.unmodifiableList(toolCalls); }
    public TokenUsage getTokenUsage() { return tokenUsage; }
    public long getCostCent() { return costCent; }
    public boolean isCacheHit() { return cacheHit; }
    public int getDurationMs() { return durationMs; }
}
