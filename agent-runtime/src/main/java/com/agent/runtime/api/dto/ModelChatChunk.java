package com.agent.runtime.api.dto;

/**
 * Streaming chat chunk DTO (T4, doc 06-runtime §3).
 *
 * <p>Maps from {@code agentplatform.model.v1.ChatChunk}: delta text + optional tool_call / finish reason.
 * Emitted by {@code ModelGatewayClient.chatStream(ModelChatRequest)}.
 */
public class ModelChatChunk {

    public enum FinishReason { NONE, STOP, LENGTH, TOOL_CALLS, CONTENT_FILTER }

    private final String callId;
    private final String delta;
    private final ModelToolCall toolCall;
    private final FinishReason finish;

    public ModelChatChunk(String callId, String delta) {
        this(callId, delta, null, FinishReason.NONE);
    }

    public ModelChatChunk(String callId, String delta, ModelToolCall toolCall, FinishReason finish) {
        this.callId = callId;
        this.delta = delta;
        this.toolCall = toolCall;
        this.finish = finish == null ? FinishReason.NONE : finish;
    }

    public static ModelChatChunk finish(String callId, FinishReason reason) {
        return new ModelChatChunk(callId, "", null, reason);
    }

    public static ModelChatChunk toolCall(String callId, ModelToolCall toolCall) {
        return new ModelChatChunk(callId, "", toolCall, FinishReason.TOOL_CALLS);
    }

    public String getCallId() { return callId; }
    public String getDelta() { return delta; }
    public ModelToolCall getToolCall() { return toolCall; }
    public FinishReason getFinish() { return finish; }
    public boolean isFinished() { return finish != FinishReason.NONE; }
    public boolean hasToolCall() { return toolCall != null; }
}
