package com.agent.tool.engine.api;

import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.model.ApprovalRecord;
import com.agent.tool.engine.model.RiskAssessment;
import com.agent.tool.engine.model.ToolCallAuditLog;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.sandbox.SandboxInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;

/**
 * Tool call auditor port (F8 audit: tool_call_log persistence).
 *
 * <p>T9 enhances the contract with a {@code record(...)} method that captures
 * the full 16-field audit context (request + risk + sandbox + result +
 * approval) plus four query methods for audit retrieval.</p>
 */
public interface ToolCallAuditor {

    /**
     * Persist audit log entry for a tool call (success or failure).
     *
     * <p>Legacy API used by {@code ToolGatewayImpl} (T8). New code should
     * prefer {@link #record} which captures the full 16-field context.</p>
     *
     * @deprecated use {@link #record} for full-context audit logging.
     */
    @Deprecated
    void audit(ToolCallAuditLog log);

    /**
     * Record a tool call audit log with full 16-field context (T9).
     *
     * <p>Constructs a {@link ToolCallAuditLog} from the request, risk
     * assessment, sandbox instance, execution result, and approval record,
     * then persists it in a new transaction ({@code REQUIRES_NEW}) so that
     * the audit entry remains visible even if the caller's transaction is
     * rolled back. Audit persistence failures are logged but never
     * propagated to the caller.</p>
     *
     * @param request    tool call request (callId, tenantId, agentId, toolId, paramsHash, traceId)
     * @param assessment risk assessment (riskLevel, requiresApproval)
     * @param sandbox    borrowed sandbox instance (containerId), or null if non-sandbox executor
     * @param result     execution result (status, output, errorStack, fromCache)
     * @param approval   approval record (approverId), or null if not required
     * @return auditLogId (the JPA entity id), or null if persistence failed
     */
    String record(ToolCallRequest request, RiskAssessment assessment,
                  SandboxInstance sandbox, ToolCallResult result,
                  ApprovalRecord approval);

    /**
     * Find audit log by call id (T9).
     */
    Optional<ToolCallAuditLog> findByCallId(String callId);

    /**
     * Find audit logs by tenant + time range with pagination (T9).
     */
    Page<ToolCallAuditLog> findByTenantIdAndTimeRange(
            String tenantId, Instant from, Instant to, Pageable pageable);

    /**
     * Find audit logs by tenant + toolId with pagination (T9).
     */
    Page<ToolCallAuditLog> findByTenantIdAndToolId(
            String tenantId, String toolId, Pageable pageable);

    /**
     * Count audit logs by tenant + status (T9).
     */
    long countByStatus(String tenantId, ToolCallStatus status);
}
