package com.agent.tool.engine.model;

import com.agent.tool.engine.enums.ToolCallStatus;

import java.io.Serializable;
import java.time.Instant;

/**
 * Tool call audit log POJO (F8 audit: tool_call_log table row).
 */
public class ToolCallAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private String logId;
    private String traceId;
    private String toolId;
    private String inputJson;
    private String output;
    private ToolCallStatus status;
    private String errorStack;
    private Instant occurredAt;
    private String tenantId;

    public ToolCallAuditLog() {
    }

    public ToolCallAuditLog(String traceId, String toolId, ToolCallStatus status) {
        this.traceId = traceId;
        this.toolId = toolId;
        this.status = status;
        this.occurredAt = Instant.now();
    }

    public String getLogId() {
        return logId;
    }

    public void setLogId(String logId) {
        this.logId = logId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
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

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
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

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
