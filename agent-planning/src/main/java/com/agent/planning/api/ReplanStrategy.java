package com.agent.planning.api;

import com.agent.planning.enums.ReplanMode;

/**
 * Replan strategy selector (doc 03-task-engine §10.1 ReplanModeSelector).
 *
 * <p>Decision rules:
 * - replanCount ≥ maxReplan → MANUAL (转人工)
 * - reason indicates root/structural change → FULL (全量重规划)
 * - otherwise → INCREMENTAL (增量重规划)
 * Skeleton stage: rule-based. ML-based decision deferred to Plan 04.</p>
 */
public interface ReplanStrategy {

    /**
     * Select replan mode based on reason + replan count.
     *
     * @param reason      replan trigger reason (e.g. "subtask_failed", "root_changed")
     * @param replanCount current replan count (0 = first replan)
     * @return replan mode (INCREMENTAL / FULL / MANUAL)
     */
    ReplanMode select(String reason, int replanCount);

    /**
     * Check if replan is allowed (count < max).
     *
     * @param replanCount current replan count
     * @return true if further replan allowed
     */
    boolean canReplan(int replanCount);
}
