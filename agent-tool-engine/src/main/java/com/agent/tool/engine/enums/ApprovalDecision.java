package com.agent.tool.engine.enums;

/**
 * Approval await result decision (doc 05-tool-engine §4.4).
 *
 * <p>Represents the outcome of {@code ApprovalStore.await(approvalId, timeout)}:
 * <ul>
 *   <li>APPROVED: approver approved within SLA</li>
 *   <li>REJECTED: approver rejected</li>
 *   <li>TIMEOUT: no decision within SLA (auto-rejected)</li>
 *   <li>PENDING: still waiting (returned only if await interrupted before SLA)</li>
 * </ul></p>
 */
public enum ApprovalDecision {

    PENDING,
    APPROVED,
    REJECTED,
    TIMEOUT
}
