package com.agent.tool.engine.model;

import com.agent.tool.engine.enums.ToolRiskLevel;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Tool call request POJO (F8 invoke entry).
 */
public class ToolCallRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String toolId;
    private String inputJson;
    private Map<String, Object> params = new HashMap<>();
    private ToolRiskLevel riskLevel;
    private String tenantId;
    private String traceId;
    private String inputHash;
    /** Caller agent id (T9 audit field, nullable for cross-agent calls). */
    private Long agentId;
    /** Task id (shard key, T9 audit field). */
    private String taskId;

    public ToolCallRequest() {
    }

    public ToolCallRequest(String toolId, String inputJson) {
        this.toolId = toolId;
        this.inputJson = inputJson;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getInputJson() {
        return inputJson;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public ToolRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(ToolRiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getInputHash() {
        return inputHash;
    }

    public void setInputHash(String inputHash) {
        this.inputHash = inputHash;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
}
