package com.agent.tool.engine.model;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Approval submission request (doc 05-tool-engine §4.3).
 *
 * <p>Carries the contextual information needed to create an
 * {@link ApprovalRecord} via {@code ApprovalStore.submit(request)}.</p>
 */
public class ApprovalRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String toolId;
    private String taskId;
    private Long agentId;
    private String tenantId;
    /** SHA-256 hash of call params; used for R2 recent-approval shortcut. */
    private String paramsHash;
    /** Input snapshot JSON for audit / replay. */
    private String inputSnapshot;
    private String applicant;
    private String reason;
    /** SLA duration; null uses default (R2=5min, R3=30min per doc 05 §4.4). */
    private Duration sla;
    /** Explicit expire-at; overrides sla when set. */
    private Instant expireAt;

    public ApprovalRequest() {
    }

    public ApprovalRequest(String toolId, String tenantId, String applicant) {
        this.toolId = toolId;
        this.tenantId = tenantId;
        this.applicant = applicant;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getParamsHash() {
        return paramsHash;
    }

    public void setParamsHash(String paramsHash) {
        this.paramsHash = paramsHash;
    }

    public String getInputSnapshot() {
        return inputSnapshot;
    }

    public void setInputSnapshot(String inputSnapshot) {
        this.inputSnapshot = inputSnapshot;
    }

    public String getApplicant() {
        return applicant;
    }

    public void setApplicant(String applicant) {
        this.applicant = applicant;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Duration getSla() {
        return sla;
    }

    public void setSla(Duration sla) {
        this.sla = sla;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Instant expireAt) {
        this.expireAt = expireAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApprovalRequest that)) return false;
        return Objects.equals(toolId, that.toolId)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(paramsHash, that.paramsHash)
                && Objects.equals(taskId, that.taskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolId, tenantId, paramsHash, taskId);
    }
}
