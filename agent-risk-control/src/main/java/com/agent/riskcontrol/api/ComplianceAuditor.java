package com.agent.riskcontrol.api;

import com.agent.riskcontrol.model.AuditLogRequest;
import com.agent.riskcontrol.model.AuditLogAck;

/**
 * Compliance audit port.
 *
 * <p>Records audit log entries for compliance tracking.
 */
public interface ComplianceAuditor {

    /**
     * Record an audit log entry.
     *
     * @param request audit log request
     * @return audit log acknowledgment with generated audit_id
     */
    AuditLogAck record(AuditLogRequest request);
}
