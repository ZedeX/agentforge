package com.agent.planning.api.impl;

import com.agent.planning.api.ComplexityScorer;
import com.agent.planning.enums.PlanComplexity;
import com.agent.planning.model.ComplexityDimensions;
import org.springframework.stereotype.Component;

/**
 * In-memory complexity scorer (doc 03-task-engine §6.2 assessor).
 *
 * <p>Scoring rules:
 * - total ≤8 → L1 / 9-14 → L2 / >14 → L3
 * - risk ≥4 forces L3 upgrade (regardless of total)
 * - null dimensions → L1 (default)
 * - individual dimension values clamped to [0, 5] before sum</p>
 *
 * <p>Skeleton stage: rule-based. ML model integration deferred to Plan 04.</p>
 */
@Component
public class ComplexityScorerImpl implements ComplexityScorer {

    private static final int L1_THRESHOLD = 8;
    private static final int L2_THRESHOLD = 14;
    private static final int RISK_UPGRADE_THRESHOLD = 4;

    @Override
    public PlanComplexity score(ComplexityDimensions dimensions) {
        if (dimensions == null) {
            return PlanComplexity.L1;
        }
        int risk = clamp(dimensions.getRisk());
        // Risk high → force L3 upgrade
        if (risk >= RISK_UPGRADE_THRESHOLD) {
            return PlanComplexity.L3;
        }
        int total = clamp(dimensions.getGoal())
                + clamp(dimensions.getExecution())
                + clamp(dimensions.getDomain())
                + clamp(dimensions.getKnowledge())
                + risk
                + clamp(dimensions.getContext());
        if (total <= L1_THRESHOLD) {
            return PlanComplexity.L1;
        }
        if (total <= L2_THRESHOLD) {
            return PlanComplexity.L2;
        }
        return PlanComplexity.L3;
    }

    /** Clamp dimension value to [0, 5]. */
    private int clamp(int v) {
        if (v < 0) {
            return 0;
        }
        if (v > 5) {
            return 5;
        }
        return v;
    }
}
