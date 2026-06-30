package com.agent.planning.api.impl;

import com.agent.planning.api.PlanValidator;
import com.agent.planning.model.Plan;
import com.agent.planning.model.PlanValidationResult;
import com.agent.planning.model.PlanningContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * In-memory plan validator (doc 03-task-engine §9 5 dimensions self-check).
 *
 * <p>5 dimensions:
 * 1. completeness (完备性): dagJson must be non-empty
 * 2. atomicity (原子性): warn if step count < 2 (single-step plan may be too coarse)
 * 3. efficiency (效率): warn if dagJson length > 10000 (large DAG may be inefficient)
 * 4. cost (成本): warn if costBudgetCent > 0 and dagJson indicates expensive (heuristic: >5000)
 * 5. fault-tolerance (容错): warn if dagJson has no retry hints</p>
 *
 * <p>Skeleton stage: rule-based. ML-based self-check deferred to Plan 04.</p>
 */
@Component
public class PlanValidatorImpl implements PlanValidator {

    private static final int LARGE_DAG_THRESHOLD = 10000;
    private static final int EXPENSIVE_BUDGET_THRESHOLD = 5000;
    private static final int MIN_STEPS_FOR_ATOMICITY = 2;
    private static final String RETRY_HINT = "retry";

    @Override
    public PlanValidationResult validate(Plan plan, PlanningContext context) {
        PlanValidationResult result = new PlanValidationResult();
        result.setPassed(true);
        result.setDimensions(List.of("completeness", "atomicity", "efficiency", "cost", "fault-tolerance"));

        // 1. completeness
        if (plan == null) {
            result.addError("completeness: plan is null");
            return result;
        }
        if (plan.getDagJson() == null || plan.getDagJson().isEmpty()) {
            result.addError("completeness: dagJson is empty");
            return result;
        }

        // 2. atomicity - heuristic: count node hints in dagJson
        int stepCount = countSteps(plan.getDagJson());
        if (stepCount < MIN_STEPS_FOR_ATOMICITY) {
            result.addWarning("atomicity: single-step plan, consider splitting");
        }

        // 3. efficiency - large DAG warning
        if (plan.getDagJson().length() > LARGE_DAG_THRESHOLD) {
            result.addWarning("efficiency: DAG too large (" + plan.getDagJson().length() + " chars), consider batching");
        }

        // 4. cost - budget check
        if (context != null && context.getCostBudgetCent() > EXPENSIVE_BUDGET_THRESHOLD) {
            result.addWarning("cost: budget exceeds " + EXPENSIVE_BUDGET_THRESHOLD + " cents, verify ROI");
        }

        // 5. fault-tolerance - retry hint check
        if (!plan.getDagJson().toLowerCase().contains(RETRY_HINT)) {
            result.addWarning("fault-tolerance: no retry hint in DAG, consider adding fallback");
        }

        return result;
    }

    /** Heuristic: count "node" occurrences in dagJson as step proxy. */
    private int countSteps(String dagJson) {
        if (dagJson == null || dagJson.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        String lower = dagJson.toLowerCase();
        while ((idx = lower.indexOf("node", idx)) != -1) {
            count++;
            idx += 4;
        }
        return count;
    }
}
