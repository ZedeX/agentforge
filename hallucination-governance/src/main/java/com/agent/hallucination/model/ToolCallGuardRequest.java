package com.agent.hallucination.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Tool call guard request (F10 L5 tool gateway param guard input).
 */
public class ToolCallGuardRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String toolId;
    private Map<String, Object> params = new HashMap<>();
    /** Required field names declared by tool schema. */
    private java.util.List<String> requiredFields;

    public ToolCallGuardRequest() {
    }

    public ToolCallGuardRequest(String toolId, Map<String, Object> params) {
        this.toolId = toolId;
        this.params = params == null ? new HashMap<>() : params;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public java.util.List<String> getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(java.util.List<String> requiredFields) {
        this.requiredFields = requiredFields;
    }
}
