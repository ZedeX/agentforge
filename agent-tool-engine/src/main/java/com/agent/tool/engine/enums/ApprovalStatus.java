package com.agent.tool.engine.enums;

/**
 * Tool approval status (doc 01-database §4.4 tool_approval.status).
 *
 * <p>PENDING=待审批, APPROVED=已批准, REJECTED=已拒绝, EXPIRED=已过期.</p>
 */
public enum ApprovalStatus {

    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED
}
