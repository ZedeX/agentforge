package com.agent.tool.engine.api;

import com.agent.tool.engine.model.ApprovalRecord;

import java.util.Optional;

/**
 * Approval store port (F8 R3 approval state machine lookup).
 */
public interface ApprovalStore {

    /**
     * Find the latest valid (non-expired) approval record for a tool.
     *
     * @return empty when no approval exists; record when approved
     */
    Optional<ApprovalRecord> findValid(String toolId);
}
