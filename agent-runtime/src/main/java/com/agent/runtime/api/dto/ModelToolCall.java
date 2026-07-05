package com.agent.runtime.api.dto;

/**
 * Tool call DTO parsed from model output (T4, doc 06-runtime §3).
 *
 * <p>Maps to {@code agentplatform.model.v1.ToolCall}: toolId + inputJson + status.
 * Used by {@code ModelGatewayClient} to return parsed tool calls to ThinkPhase.
 */
public class ModelToolCall {

    private final String callId;
    private final String toolId;
    private final String inputJson;
    private final String status;

    public ModelToolCall(String toolId, String inputJson) {
        this(null, toolId, inputJson, "pending");
    }

    public ModelToolCall(String callId, String toolId, String inputJson, String status) {
        this.callId = callId;
        this.toolId = toolId;
        this.inputJson = inputJson;
        this.status = status;
    }

    public String getCallId() { return callId; }
    public String getToolId() { return toolId; }
    public String getInputJson() { return inputJson; }
    public String getStatus() { return status; }
}
