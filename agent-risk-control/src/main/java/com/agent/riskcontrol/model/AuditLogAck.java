package com.agent.riskcontrol.model;

/**
 * POJO for audit log acknowledgment.
 */
public class AuditLogAck {

    private String auditId;

    public AuditLogAck() {
    }

    public AuditLogAck(String auditId) {
        this.auditId = auditId;
    }

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }
}
