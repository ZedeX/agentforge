package com.agent.planning.api.impl;

import com.agent.planning.api.ReplanStrategy;
import com.agent.planning.enums.ReplanMode;
import org.springframework.stereotype.Component;

/**
 * In-memory replan strategy selector (doc 03-task-engine §10.1 ReplanModeSelector).
 *
 * <p>Decision rules:
 * - replanCount ≥ MAX_REPLAN (3) → MANUAL (转人工)
 * - reason contains "root" or "structural" → FULL (全量重规划)
 * - reason contains "structural" + count ≥ 2 → MANUAL
 * - otherwise → INCREMENTAL (增量重规划)</p>
 *
 * <p>Skeleton stage: rule-based. ML-based decision deferred to Plan 04.</p>
 */
@Component
public class ReplanStrategyImpl implements ReplanStrategy {

    private static final int MAX_REPLAN = 3;
    private static final int STRUCTURAL_MANUAL_THRESHOLD = 2;

    @Override
    public ReplanMode select(String reason, int replanCount) {
        // 1. count exceeded → manual
        if (replanCount >= MAX_REPLAN) {
            return ReplanMode.MANUAL;
        }
        String normalizedReason = reason == null ? "" : reason.toLowerCase();
        // 2. structural change + count ≥ threshold → manual
        if (normalizedReason.contains("structural") && replanCount >= STRUCTURAL_MANUAL_THRESHOLD) {
            return ReplanMode.MANUAL;
        }
        // 3. root node change → full
        if (normalizedReason.contains("root") || normalizedReason.contains("structural")) {
            return ReplanMode.FULL;
        }
        // 4. default → incremental
        return ReplanMode.INCREMENTAL;
    }

    @Override
    public boolean canReplan(int replanCount) {
        return replanCount < MAX_REPLAN;
    }
}
