package com.agent.planning.api;

import com.agent.planning.enums.PlanComplexity;
import com.agent.planning.model.ComplexityDimensions;

/**
 * Complexity scorer (doc 03-task-engine §6.2 assessor, PRD §二(三) complexity assessment).
 *
 * <p>6 dimensions scored 1-5 each (total 6-30). Scoring rules:
 * total ≤8 → L1 / 9-14 → L2 / >14 → L3; risk ≥4 forces L3 upgrade.
 * Skeleton stage: rule-based scoring. ML model integration deferred to Plan 04.</p>
 */
public interface ComplexityScorer {

    /**
     * Score complexity from 6 dimensions.
     *
     * @param dimensions 6-dimension scores (goal/execution/domain/knowledge/risk/context)
     * @return complexity level (L1/L2/L3)
     */
    PlanComplexity score(ComplexityDimensions dimensions);
}
