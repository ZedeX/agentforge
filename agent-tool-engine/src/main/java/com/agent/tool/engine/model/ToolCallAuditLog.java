package com.agent.tool.engine.model;

import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.enums.ToolRiskLevel;

import java.io.Serializable;
import java.time.Instant;

/**
 * Tool call audit log POJO (F8 audit: tool_call_log table row).
 *
 * <p>T9 enhances this POJO to carry the 16 fields required by doc 05 §T9:
 * callId / tenantId / agentId / toolId / paramsHash / status / riskLevel /
 * startedAt / endedAt / durationMs / costTokens / exitCode / errorMessage /
 * sandboxContainerId / approverId / cacheHit. Legacy fields (logId / traceId /
 * inputJson / output / errorStack / occurredAt) are retained for backward
 * compatibility with the {@code audit(ToolCallAuditLog)} API used by
 * {@code ToolGatewayImpl} (T8).</p>
 */
public class ToolCallAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    // ===== Legacy fields (used by audit() API + T8 ToolGatewayImpl) =====
    private String logId;
    private String traceId;
    private String toolId;
    private String inputJson;
    private String output;
    private ToolCallStatus status;
    private String errorStack;
    private Instant occurredAt;
    private String tenantId;

    // ===== T9 enhanced fields (16-field audit log) =====
    /** T9 field 1: call id (uk_call_id). */
    private String callId;
    /** T9 field 3: agent id (FK to agent entity). */
    private Long agentId;
    /** T9 field 5: params hash (ParamsHasher 64-char hex). */
    private String paramsHash;
    /** T9 field 6: tool version. */
    private int toolVersion;
    /** T9 field 7: risk level (R1=1, R2=2, R3=3). */
    private ToolRiskLevel riskLevel;
    /** T9 field 8: started at. */
    private Instant startedAt;
    /** T9 field 9: ended at. */
    private Instant endedAt;
    /** T9 field 10: duration millis. */
    private long durationMs;
    /** T9 field 11: cost tokens (LLM token usage). */
    private int costTokens;
    /** T9 field 12: exit code (sandbox exec exit code, null if non-sandbox). */
    private Integer exitCode;
    /** T9 field 13: error message. */
    private String errorMessage;
    /** T9 field 14: sandbox container id (null if non-sandbox). */
    private String sandboxContainerId;
    /** T9 field 15: approver id (R3 only, null otherwise). */
    private String approverId;
    /** T9 field 16: cache hit flag. */
    private boolean cacheHit;
    /** Task id (shard key). */
    private String taskId;
    /** Step number within task. */
    private Integer stepNo;

    public ToolCallAuditLog() {
    }

    /** Legacy constructor (traceId + toolId + status, occurredAt = now). */
    public ToolCallAuditLog(String traceId, String toolId, ToolCallStatus status) {
        this.traceId = traceId;
        this.toolId = toolId;
        this.status = status;
        this.occurredAt = Instant.now();
        this.startedAt = occurredAt;
    }

    // ===== Legacy getters/setters =====

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

    // ===== T9 enhanced getters/setters =====

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getParamsHash() {
        return paramsHash;
    }

    public void setParamsHash(String paramsHash) {
        this.paramsHash = paramsHash;
    }

    public int getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(int toolVersion) {
        this.toolVersion = toolVersion;
    }

    public ToolRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(ToolRiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getCostTokens() {
        return costTokens;
    }

    public void setCostTokens(int costTokens) {
        this.costTokens = costTokens;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSandboxContainerId() {
        return sandboxContainerId;
    }

    public void setSandboxContainerId(String sandboxContainerId) {
        this.sandboxContainerId = sandboxContainerId;
    }

    public String getApproverId() {
        return approverId;
    }

    public void setApproverId(String approverId) {
        this.approverId = approverId;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public void setCacheHit(boolean cacheHit) {
        this.cacheHit = cacheHit;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getStepNo() {
        return stepNo;
    }

    public void setStepNo(Integer stepNo) {
        this.stepNo = stepNo;
    }
}
