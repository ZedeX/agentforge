package com.agent.tool.engine.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool input/output schema POJO (doc 02-api §3.1 three-layer schema).
 *
 * <p>Holds required field names + a simple type map for skeleton-level validation.</p>
 */
public class ToolSchema implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<String> requiredFields = new ArrayList<>();
    private int maxInputToken;

    public ToolSchema() {
    }

    public ToolSchema(List<String> requiredFields) {
        this.requiredFields = requiredFields == null ? new ArrayList<>() : new ArrayList<>(requiredFields);
    }

    public List<String> getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(List<String> requiredFields) {
        this.requiredFields = requiredFields;
    }

    public int getMaxInputToken() {
        return maxInputToken;
    }

    public void setMaxInputToken(int maxInputToken) {
        this.maxInputToken = maxInputToken;
    }
}
