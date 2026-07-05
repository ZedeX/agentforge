package com.agent.runtime.loop;

/**
 * Tool call decision parsed from Think phase model output (T3, doc 06-runtime §2 F6 think).
 *
 * <p>Carries tool identifier, arguments JSON, and optional reasoning trace.
 * Immutable value object; populated by {@code ThinkPhase} via builder-style setters.</p>
 */
public class ToolCallDecision {

    private String toolId;
    private String paramsJson;
    private String reasoning;

    public ToolCallDecision() {
    }

    public ToolCallDecision(String toolId, String paramsJson) {
        this.toolId = toolId;
        this.paramsJson = paramsJson;
    }

    public ToolCallDecision(String toolId, String paramsJson, String reasoning) {
        this.toolId = toolId;
        this.paramsJson = paramsJson;
        this.reasoning = reasoning;
    }

    public String getToolId() { return toolId; }
    public void setToolId(String toolId) { this.toolId = toolId; }

    public String getParamsJson() { return paramsJson; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public boolean isValid() {
        return toolId != null && !toolId.isEmpty();
    }
}
