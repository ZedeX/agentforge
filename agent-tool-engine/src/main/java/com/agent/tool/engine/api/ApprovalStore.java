package com.agent.tool.engine.api;

import com.agent.tool.engine.enums.ApprovalDecision;
import com.agent.tool.engine.enums.ApprovalStatus;
import com.agent.tool.engine.model.ApprovalRecord;
import com.agent.tool.engine.model.ApprovalRequest;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Approval store port (doc 05-tool-engine §4.3 / §4.4 approval lifecycle).
 *
 * <p>Full lifecycle: submit → await → approve/reject → expire cleanup.
 * Used by the gateway (R3 approval check) and the risk classifier
 * (R2 recent-approval shortcut).</p>
 */
public interface ApprovalStore {

    // ============ Gateway / Classifier lookups ============

    /**
     * Find the latest valid (non-expired) approval record for a tool.
     *
     * <p>Used by the gateway for R3 approval check.</p>
     *
     * @return empty when no approval exists; record when approved
     */
    Optional<ApprovalRecord> findValid(String toolId);

    /**
     * Find a recent approved record matching tenant + toolId + paramsHash
     * within the given lookback duration.
     *
     * <p>Used by the risk classifier for R2 approval shortcut (doc 05 §4.3:
     * "R2 → 同租户最近 1h 内已批过且参数同 → 自动放行").</p>
     *
     * @param tenantId   tenant id (may be null → empty)
     * @param toolId     tool id (may be null → empty)
     * @param paramsHash params hash (may be null → matches any params)
     * @param lookback   lookback duration from now (must not be null)
     * @return optional matching approved record
     */
    default Optional<ApprovalRecord> findRecentApproved(
            String tenantId, String toolId, String paramsHash, Duration lookback) {
        return Optional.empty();
    }

    // ============ Approval lifecycle (T5) ============

    /**
     * Submit an approval request and persist it as PENDING.
     *
     * @param request approval request (toolId + applicant required)
     * @return the generated approvalId
     */
    String submit(ApprovalRequest request);

    /**
     * Block until the approver decides or SLA elapses.
     *
     * @param approvalId approval id returned by {@link #submit}
     * @param timeout    max wait duration (SLA)
     * @return decision; {@link ApprovalDecision#TIMEOUT} if SLA elapses
     *         without a decision
     */
    ApprovalDecision await(String approvalId, Duration timeout);

    /**
     * Approve a pending approval.
     *
     * <p>Idempotency: approving an already-decided approval throws
     * {@code ToolApprovalException}.</p>
     *
     * @param approvalId approval id
     * @param approver   approver user id
     * @param comment    optional approval comment
     */
    void approve(String approvalId, String approver, String comment);

    /**
     * Reject a pending approval.
     *
     * @param approvalId approval id
     * @param approver   approver user id
     * @param comment    optional rejection reason
     */
    void reject(String approvalId, String approver, String comment);

    /**
     * Find pending approvals assigned to / visible by an approver.
     *
     * @param approver approver user id
     * @return list of pending approval records
     */
    List<ApprovalRecord> findPendingByApprover(String approver);

    /**
     * Cleanup expired PENDING records (mark as EXPIRED).
     *
     * <p>Called by a {@code @Scheduled} task in production.</p>
     *
     * @return number of records cleaned up
     */
    int cleanupExpired();
}
