package com.agent.planning.api;

import com.agent.planning.model.Plan;
import com.agent.planning.model.PlanValidationResult;
import com.agent.planning.model.PlanningContext;

/**
 * Plan validator (doc 03-task-engine §8.2.1 validatePlan, §9 5 dimensions self-check).
 *
 * <p>5 dimensions: completeness / atomicity / efficiency / cost / fault-tolerance.
 * Skeleton stage: rule-based validation. ML-based self-check deferred to Plan 04.</p>
 */
public interface PlanValidator {

    /**
     * Validate a plan against 5 dimensions.
     *
     * @param plan    plan to validate (must have non-null dagJson)
     * @param context planning context (for cost budget check)
     * @return validation result with passed flag + errors + warnings
     */
    PlanValidationResult validate(Plan plan, PlanningContext context);
}
