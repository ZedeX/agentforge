package com.agent.planning.api;

import com.agent.planning.model.Plan;
import com.agent.planning.model.PlanValidationResult;
import com.agent.planning.model.PlanningContext;
import com.agent.planning.model.ReplanContext;

/**
 * Planning service (doc 03-task-engine §8.2.1, PRD §二(三) planning orchestration).
 *
 * <p>Core orchestration interface: assessComplexity → plan → validatePlan → replan.
 * Skeleton stage: in-memory orchestration. gRPC server + AI planner deferred to Plan 04 deepening.</p>
 */
public interface PlanningService {

    /**
     * Assess task complexity (6 dimensions → L1/L2/L3).
     *
     * @param context planning context with task metadata
     * @return plan with complexity populated (status=DRAFT)
     */
    Plan assessComplexity(PlanningContext context);

    /**
     * Generate a plan for the given task.
     *
     * <p>Strategy: preferTemplate + template match → TEMPLATE source;
     * otherwise fall back to AI planner → ai source.</p>
     *
     * @param context planning context
     * @return generated plan (status=DRAFT, with dagJson populated)
     */
    Plan plan(PlanningContext context);

    /**
     * Validate a plan against 5 dimensions (completeness/atomicity/efficiency/cost/fault-tolerance).
     *
     * @param plan    plan to validate
     * @param context planning context for cost budget check
     * @return validation result with errors/warnings
     */
    PlanValidationResult validatePlan(Plan plan, PlanningContext context);

    /**
     * Replan after subtask failure or root node change.
     *
     * @param context replan context with reason + previous DAG + count
     * @return new plan (version incremented, replanCount incremented)
     */
    Plan replan(ReplanContext context);
}
