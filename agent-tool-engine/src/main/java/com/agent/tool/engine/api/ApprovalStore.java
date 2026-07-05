package com.agent.tool.engine.api;

import com.agent.tool.engine.model.ApprovalRecord;

import java.time.Duration;
import java.util.Optional;

/**
 * Approval store port (doc 05-tool-engine §4.3 approval state machine).
 *
 * <p>Provides lookup of valid (non-expired) approval records for the risk
 * classifier and the gateway. The full approval lifecycle (submit / await /
 * approve / reject) is added in T5.</p>
 */
public interface ApprovalStore {

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
     * <p>Default implementation returns empty (no shortcut). T5 JPA-backed
     * implementation overrides this to query the approval table.</p>
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
}
