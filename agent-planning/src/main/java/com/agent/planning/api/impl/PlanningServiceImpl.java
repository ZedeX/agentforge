package com.agent.planning.api.impl;

import com.agent.planning.api.ComplexityScorer;
import com.agent.planning.api.PlanValidator;
import com.agent.planning.api.PlanningService;
import com.agent.planning.api.PlanRepository;
import com.agent.planning.api.ReplanStrategy;
import com.agent.planning.api.TemplateMatcher;
import com.agent.planning.enums.PlanComplexity;
import com.agent.planning.enums.PlanStatus;
import com.agent.planning.enums.ReplanMode;
import com.agent.planning.model.ComplexityDimensions;
import com.agent.planning.model.Plan;
import com.agent.planning.model.PlanTemplate;
import com.agent.planning.model.PlanValidationResult;
import com.agent.planning.model.PlanningContext;
import com.agent.planning.model.ReplanContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory planning service (doc 03-task-engine §8.2.1).
 *
 * <p>Orchestrates: assessComplexity → plan → validatePlan → replan.
 * Delegates to ComplexityScorer / TemplateMatcher / PlanValidator / ReplanStrategy.
 * Skeleton stage: in-memory. gRPC server + AI planner deferred to Plan 04 deepening.</p>
 */
@Component
public class PlanningServiceImpl implements PlanningService {

    private final ComplexityScorer complexityScorer;
    private final TemplateMatcher templateMatcher;
    private final PlanValidator planValidator;
    private final ReplanStrategy replanStrategy;
    private final PlanRepository planRepository;
    private final AtomicInteger planIdSeq = new AtomicInteger(0);

    public PlanningServiceImpl(ComplexityScorer complexityScorer,
                               TemplateMatcher templateMatcher,
                               PlanValidator planValidator,
                               ReplanStrategy replanStrategy,
                               PlanRepository planRepository) {
        this.complexityScorer = complexityScorer;
        this.templateMatcher = templateMatcher;
        this.planValidator = planValidator;
        this.replanStrategy = replanStrategy;
        this.planRepository = planRepository;
    }

    @Override
    public Plan assessComplexity(PlanningContext context) {
        if (context == null) {
            return null;
        }
        ComplexityDimensions dims = buildDimensions(context);
        PlanComplexity complexity = complexityScorer.score(dims);
        Plan plan = new Plan(generatePlanId(), context.getTaskId());
        plan.setComplexity(complexity);
        plan.setStatus(PlanStatus.DRAFT);
        plan.setTenantId(context.getTenantId());
        plan.setUserId(context.getUserId());
        planRepository.save(plan);
        return plan;
    }

    @Override
    public Plan plan(PlanningContext context) {
        if (context == null) {
            return null;
        }
        // 1. assess complexity first
        Plan plan = assessComplexity(context);
        // 2. L1 → skip template matching, return simple plan
        if (plan.getComplexity() == PlanComplexity.L1) {
            plan.setDagJson(buildSimpleDag(context));
            plan.setSource("direct");
            planRepository.save(plan);
            return plan;
        }
        // 3. L2/L3 → try template match (if preferTemplate)
        if (context.isPreferTemplate()) {
            Optional<PlanTemplate> matched = templateMatcher.match(
                    context.getTaskSchemaJson(), List.of("default"));
            if (matched.isPresent()) {
                plan.setDagJson(matched.get().getDagJson());
                plan.setSource("template");
                plan.setTemplateId(matched.get().getId());
                planRepository.save(plan);
                return plan;
            }
        }
        // 4. fallback to AI planner (mock)
        plan.setDagJson(buildAiDag(context));
        plan.setSource("ai");
        planRepository.save(plan);
        return plan;
    }

    @Override
    public PlanValidationResult validatePlan(Plan plan, PlanningContext context) {
        return planValidator.validate(plan, context);
    }

    @Override
    public Plan replan(ReplanContext context) {
        if (context == null) {
            return null;
        }
        ReplanMode mode = replanStrategy.select(context.getReason(), context.getReplanCount());
        // MANUAL mode → no auto replan, return null to signal manual intervention
        if (mode == ReplanMode.MANUAL) {
            return null;
        }
        Plan plan = new Plan(generatePlanId(), context.getTaskId());
        plan.setReplanCount(context.getReplanCount() + 1);
        plan.setVersion(context.getReplanCount() + 2);
        plan.setStatus(PlanStatus.DRAFT);
        plan.setTenantId(context.getTenantId());
        // FULL → regenerate entire DAG; INCREMENTAL → patch failed node only
        if (mode == ReplanMode.FULL) {
            plan.setDagJson(buildAiDagFromPrev(context.getPreviousDagJson()));
            plan.setSource("ai");
        } else {
            plan.setDagJson(patchFailedNode(context.getPreviousDagJson(), context.getFailedNodeId()));
            plan.setSource("ai");
        }
        planRepository.save(plan);
        return plan;
    }

    // ===== 内部辅助 =====

    private ComplexityDimensions buildDimensions(PlanningContext context) {
        // Heuristic: derive 6 dimensions from goal length + budget
        int goalScore = clamp(context.getGoal() == null ? 3 : Math.min(5, context.getGoal().length() / 10 + 1));
        int budget = (int) Math.min(5, context.getCostBudgetCent() / 1000);
        return new ComplexityDimensions(goalScore, budget, 1, 1, 1, 1);
    }

    private String generatePlanId() {
        return "plan-" + planIdSeq.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String buildSimpleDag(PlanningContext context) {
        return "{\"nodes\":[{\"id\":\"n1\",\"title\":\"" + safe(context.getGoal()) + "\"}],\"edges\":[]}";
    }

    private String buildAiDag(PlanningContext context) {
        return "{\"nodes\":[{\"id\":\"n1\"},{\"id\":\"n2\"}],\"edges\":[{\"from\":\"n1\",\"to\":\"n2\"}],\"retry\":2}";
    }

    private String buildAiDagFromPrev(String prevDagJson) {
        if (prevDagJson == null || prevDagJson.isEmpty()) {
            return buildAiDag(null);
        }
        return prevDagJson + "_v2_replan";
    }

    private String patchFailedNode(String prevDagJson, String failedNodeId) {
        if (prevDagJson == null || prevDagJson.isEmpty()) {
            return "{\"nodes\":[],\"edges\":[],\"retry\":1}";
        }
        if (failedNodeId == null) {
            return prevDagJson + "_patched";
        }
        return prevDagJson.replace(failedNodeId, failedNodeId + "_retry");
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private int clamp(int v) {
        if (v < 0) return 0;
        if (v > 5) return 5;
        return v;
    }
}
