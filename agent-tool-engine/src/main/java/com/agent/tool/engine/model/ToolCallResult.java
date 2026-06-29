package com.agent.tool.engine.model;

import com.agent.tool.engine.enums.ToolCallStatus;

import java.io.Serializable;

/**
 * Tool call result POJO (F8 output / cache / audit).
 */
public class ToolCallResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String toolId;
    private String output;
    private int outputTokens;
    private ToolCallStatus status;
    private String errorStack;
    private boolean fromCache;

    public ToolCallResult() {
    }

    public ToolCallResult(String toolId, String output, ToolCallStatus status) {
        this.toolId = toolId;
        this.output = output;
        this.status = status;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    public ToolCallStatus getStatus() {
        return status;
    }

    public void setStatus(ToolCallStatus status) {
        this.status = status;
    }

    public String getErrorStack() {
        return errorStack;
    }

    public void setErrorStack(String errorStack) {
        this.errorStack = errorStack;
    }

    public boolean isFromCache() {
        return fromCache;
    }

    public void setFromCache(boolean fromCache) {
        this.fromCache = fromCache;
    }
}
