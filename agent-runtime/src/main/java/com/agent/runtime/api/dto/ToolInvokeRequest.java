package com.agent.runtime.api.dto;

/**
 * Tool invocation request DTO (T5, doc 06-runtime §4).
 *
 * <p>Maps to {@code agentplatform.tool.v1.ToolInvokeRequest}: callId + taskId + stepNo +
 * agentId + toolId + toolVersion + inputJson + riskLevel + promptCacheKey.
 *
 * <p>Builder-style construction for readable call sites.
 */
public class ToolInvokeRequest {

    private String callId;
    private String taskId;
    private int stepNo;
    private long agentId;
    private String toolId;
    private int toolVersion;
    private String inputJson;
    private int riskLevel;        // 1=R1 2=R2 3=R3
    private String promptCacheKey;
    private long timeoutMs = 60_000L;
    private boolean noCache = false;

    public static Builder builder() { return new Builder(); }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public int getStepNo() { return stepNo; }
    public void setStepNo(int stepNo) { this.stepNo = stepNo; }
    public long getAgentId() { return agentId; }
    public void setAgentId(long agentId) { this.agentId = agentId; }
    public String getToolId() { return toolId; }
    public void setToolId(String toolId) { this.toolId = toolId; }
    public int getToolVersion() { return toolVersion; }
    public void setToolVersion(int toolVersion) { this.toolVersion = toolVersion; }
    public String getInputJson() { return inputJson; }
    public void setInputJson(String inputJson) { this.inputJson = inputJson; }
    public int getRiskLevel() { return riskLevel; }
    public void setRiskLevel(int riskLevel) { this.riskLevel = riskLevel; }
    public String getPromptCacheKey() { return promptCacheKey; }
    public void setPromptCacheKey(String promptCacheKey) { this.promptCacheKey = promptCacheKey; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public boolean isNoCache() { return noCache; }
    public void setNoCache(boolean noCache) { this.noCache = noCache; }

    public static class Builder {
        private final ToolInvokeRequest req = new ToolInvokeRequest();

        public Builder callId(String v) { req.callId = v; return this; }
        public Builder taskId(String v) { req.taskId = v; return this; }
        public Builder stepNo(int v) { req.stepNo = v; return this; }
        public Builder agentId(long v) { req.agentId = v; return this; }
        public Builder toolId(String v) { req.toolId = v; return this; }
        public Builder toolVersion(int v) { req.toolVersion = v; return this; }
        public Builder inputJson(String v) { req.inputJson = v; return this; }
        public Builder riskLevel(int v) { req.riskLevel = v; return this; }
        public Builder promptCacheKey(String v) { req.promptCacheKey = v; return this; }
        public Builder timeoutMs(long v) { req.timeoutMs = v; return this; }
        public Builder noCache(boolean v) { req.noCache = v; return this; }

        public ToolInvokeRequest build() { return req; }
    }
}
