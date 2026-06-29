package com.agent.tool.engine.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * R3 tool approval record POJO (F8 R3 dual approval state machine).
 *
 * <p>Status flow: PENDING -> PARTIALLY_APPROVED -> APPROVED -> (expired after window).</p>
 */
public class ApprovalRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PARTIALLY_APPROVED = "PARTIALLY_APPROVED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private String approvalId;
    private String toolId;
    private String status;
    private String primaryApprover;
    private String secondaryApprover;
    private Instant approvedAt;
    private Instant expireAt;
    /** Approval validity window in seconds (default 3600 = 1h). */
    private long validityWindowSeconds = 3600L;

    public ApprovalRecord() {
    }

    public String getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPrimaryApprover() {
        return primaryApprover;
    }

    public void setPrimaryApprover(String primaryApprover) {
        this.primaryApprover = primaryApprover;
    }

    public String getSecondaryApprover() {
        return secondaryApprover;
    }

    public void setSecondaryApprover(String secondaryApprover) {
        this.secondaryApprover = secondaryApprover;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Instant expireAt) {
        this.expireAt = expireAt;
    }

    public long getValidityWindowSeconds() {
        return validityWindowSeconds;
    }

    public void setValidityWindowSeconds(long validityWindowSeconds) {
        this.validityWindowSeconds = validityWindowSeconds;
    }
}
