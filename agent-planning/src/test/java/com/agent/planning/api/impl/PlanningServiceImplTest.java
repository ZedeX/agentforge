package com.agent.planning.api.impl;

import com.agent.planning.enums.PlanStatus;
import com.agent.planning.model.Plan;
import com.agent.planning.model.PlanTemplate;
import com.agent.planning.model.PlanValidationResult;
import com.agent.planning.model.PlanningContext;
import com.agent.planning.model.ReplanContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PlanningServiceImpl unit tests (doc 03-task-engine §8.2.1).
 *
 * <p>Uses real Impl instances (ComplexityScorer / TemplateMatcher / PlanValidator /
 * ReplanStrategy / PlanRepository) to validate the orchestration logic end-to-end.</p>
 */
@DisplayName("PlanningServiceImpl 规划服务编排器")
class PlanningServiceImplTest {

    private ComplexityScorerImpl complexityScorer;
    private TemplateMatcherImpl templateMatcher;
    private PlanValidatorImpl planValidator;
    private ReplanStrategyImpl replanStrategy;
    private PlanRepositoryImpl planRepository;
    private PlanningServiceImpl service;

    @BeforeEach
    void setUp() {
        complexityScorer = new ComplexityScorerImpl();
        templateMatcher = new TemplateMatcherImpl();
        planValidator = new PlanValidatorImpl();
        replanStrategy = new ReplanStrategyImpl();
        planRepository = new PlanRepositoryImpl();
        service = new PlanningServiceImpl(complexityScorer, templateMatcher,
                planValidator, replanStrategy, planRepository);
    }

    @Test
    @DisplayName("assessComplexity 简单任务返回 L1 计划")
    void should_ReturnL1Plan_When_SimpleTask() {
        PlanningContext ctx = new PlanningContext("tk-1", "查订单", "t1");

        Plan plan = service.assessComplexity(ctx);

        assertThat(plan).isNotNull();
        assertThat(plan.getTaskId()).isEqualTo("tk-1");
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(plan.getPlanId()).isNotNull();
    }

    @Test
    @DisplayName("assessComplexity null context 返回 null")
    void should_ReturnNull_When_ContextNull() {
        assertThat(service.assessComplexity(null)).isNull();
    }

    @Test
    @DisplayName("plan L1 任务返回 source=direct")
    void should_ReturnDirectSource_When_L1Task() {
        PlanningContext ctx = new PlanningContext("tk-1", "查询", "t1");

        Plan plan = service.plan(ctx);

        assertThat(plan).isNotNull();
        assertThat(plan.getSource()).isEqualTo("direct");
        assertThat(plan.getDagJson()).isNotNull();
    }

    @Test
    @DisplayName("plan L2 任务 + 模板命中返回 source=template")
    void should_ReturnTemplateSource_When_TemplateMatched() {
        // 注册一个高成功率的模板
        PlanTemplate tpl = new PlanTemplate("周报模板", List.of("default"), "{\"nodes\":[],\"edges\":[],\"retry\":1}");
        tpl.setId(42L);
        tpl.setSuccessRate(0.9);
        templateMatcher.register(tpl);

        // 构造 L2 任务 (budget 较高使复杂度升级)
        PlanningContext ctx = new PlanningContext("tk-2", "very complex long goal here", "t1");
        ctx.setCostBudgetCent(5000);  // 5000/1000 = 5 → execution=5 → total 较高

        Plan plan = service.plan(ctx);

        assertThat(plan).isNotNull();
        assertThat(plan.getSource()).isEqualTo("template");
        assertThat(plan.getTemplateId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("plan L2 任务 + 无模板命中回退到 source=ai")
    void should_ReturnAiSource_When_NoTemplateMatched() {
        // 不注册任何模板
        // 构造 L2/L3 任务
        PlanningContext ctx = new PlanningContext("tk-3", "very complex long goal here", "t1");
        ctx.setCostBudgetCent(5000);

        Plan plan = service.plan(ctx);

        assertThat(plan).isNotNull();
        assertThat(plan.getSource()).isEqualTo("ai");
        assertThat(plan.getDagJson()).isNotNull();
    }

    @Test
    @DisplayName("plan preferTemplate=false 时跳过模板直接走 AI")
    void should_GoAiDirectly_When_PreferTemplateFalse() {
        // 注册模板但 preferTemplate=false 应跳过
        PlanTemplate tpl = new PlanTemplate("周报模板", List.of("default"), "{\"nodes\":[],\"edges\":[],\"retry\":1}");
        tpl.setId(42L);
        tpl.setSuccessRate(0.9);
        templateMatcher.register(tpl);

        PlanningContext ctx = new PlanningContext("tk-4", "very complex long goal here", "t1");
        ctx.setCostBudgetCent(5000);
        ctx.setPreferTemplate(false);

        Plan plan = service.plan(ctx);

        assertThat(plan.getSource()).isEqualTo("ai");
        assertThat(plan.getTemplateId()).isNull();
    }

    @Test
    @DisplayName("plan null context 返回 null")
    void should_ReturnNull_When_PlanContextNull() {
        assertThat(service.plan(null)).isNull();
    }

    @Test
    @DisplayName("validatePlan 委托给 PlanValidator 并返回结果")
    void should_DelegateToValidator_When_ValidatePlanCalled() {
        Plan plan = new Plan("p-1", "tk-1");
        plan.setDagJson("{\"node\":\"n1\"},{\"node\":\"n2\"},\"retry\":1");
        PlanningContext ctx = new PlanningContext("tk-1", "goal", "t1");

        PlanValidationResult result = service.validatePlan(plan, ctx);

        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("validatePlan null plan 返回 fail")
    void should_ReturnFail_When_PlanNull() {
        PlanValidationResult result = service.validatePlan(null, new PlanningContext("tk", "g", "t"));
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    @DisplayName("replan INCREMENTAL 模式返回新版本计划")
    void should_ReturnIncrementalPlan_When_ReplanCalled() {
        ReplanContext ctx = new ReplanContext("tk-1", "subtask_failed", 0);
        ctx.setPreviousDagJson("{\"nodes\":[\"n1\"]}");
        ctx.setFailedNodeId("n1");

        Plan plan = service.replan(ctx);

        assertThat(plan).isNotNull();
        assertThat(plan.getReplanCount()).isEqualTo(1);
        assertThat(plan.getVersion()).isEqualTo(2);
        assertThat(plan.getDagJson()).isNotNull();
    }

    @Test
    @DisplayName("replan FULL 模式重新生成整个 DAG")
    void should_ReturnFullReplan_When_RootChanged() {
        ReplanContext ctx = new ReplanContext("tk-1", "root_changed", 0);
        ctx.setPreviousDagJson("{\"nodes\":[\"n1\"]}");

        Plan plan = service.replan(ctx);

        assertThat(plan).isNotNull();
        assertThat(plan.getDagJson()).contains("v2_replan");
    }

    @Test
    @DisplayName("replan MANUAL 模式 (count 超限) 返回 null")
    void should_ReturnNull_When_ManualMode() {
        ReplanContext ctx = new ReplanContext("tk-1", "subtask_failed", 3);  // count ≥3 → MANUAL

        assertThat(service.replan(ctx)).isNull();
    }

    @Test
    @DisplayName("replan null context 返回 null")
    void should_ReturnNull_When_ReplanContextNull() {
        assertThat(service.replan(null)).isNull();
    }

    @Test
    @DisplayName("replan INCREMENTAL + null previousDagJson 返回默认 DAG")
    void should_ReturnDefaultDag_When_PreviousDagNull() {
        ReplanContext ctx = new ReplanContext("tk-1", "subtask_failed", 0);
        ctx.setPreviousDagJson(null);
        ctx.setFailedNodeId("n1");

        Plan plan = service.replan(ctx);

        assertThat(plan).isNotNull();
        assertThat(plan.getDagJson()).isNotNull();
    }

    @Test
    @DisplayName("replan INCREMENTAL + null failedNodeId 返回 patched DAG")
    void should_ReturnPatchedDag_When_FailedNodeIdNull() {
        ReplanContext ctx = new ReplanContext("tk-1", "subtask_failed", 0);
        ctx.setPreviousDagJson("{\"nodes\":[\"n1\"]}");
        ctx.setFailedNodeId(null);

        Plan plan = service.replan(ctx);

        assertThat(plan).isNotNull();
        assertThat(plan.getDagJson()).contains("_patched");
    }

    @Test
    @DisplayName("assessComplexity 后计划已持久化到 repository")
    void should_PersistPlan_When_Assessed() {
        PlanningContext ctx = new PlanningContext("tk-persist", "goal", "t1");

        Plan plan = service.assessComplexity(ctx);

        assertThat(planRepository.findById(plan.getPlanId())).isPresent();
    }

    @Test
    @DisplayName("plan 后计划已持久化到 repository")
    void should_PersistPlan_When_Planned() {
        PlanningContext ctx = new PlanningContext("tk-persist2", "goal", "t1");

        Plan plan = service.plan(ctx);

        assertThat(planRepository.findById(plan.getPlanId())).isPresent();
        assertThat(planRepository.findByTaskId("tk-persist2")).isNotEmpty();
    }

    @Test
    @DisplayName("replan 后新计划已持久化到 repository")
    void should_PersistPlan_When_Replanned() {
        ReplanContext ctx = new ReplanContext("tk-replan", "subtask_failed", 0);
        ctx.setPreviousDagJson("{}");

        Plan plan = service.replan(ctx);

        assertThat(planRepository.findById(plan.getPlanId())).isPresent();
    }
}
