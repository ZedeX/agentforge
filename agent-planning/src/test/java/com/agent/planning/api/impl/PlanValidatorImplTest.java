package com.agent.planning.api.impl;

import com.agent.planning.model.Plan;
import com.agent.planning.model.PlanValidationResult;
import com.agent.planning.model.PlanningContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PlanValidatorImpl unit tests (doc 03-task-engine §9 5 dimensions self-check).
 */
@DisplayName("PlanValidatorImpl 计划校验器")
class PlanValidatorImplTest {

    private final PlanValidatorImpl validator = new PlanValidatorImpl();

    @Test
    @DisplayName("plan null 时返回 fail 且包含 completeness 错误")
    void should_FailWithError_When_PlanNull() {
        PlanValidationResult result = validator.validate(null, new PlanningContext("tk-1", "goal", "t1"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("completeness"));
    }

    @Test
    @DisplayName("dagJson null 时返回 fail 且包含 completeness 错误")
    void should_FailWithError_When_DagJsonNull() {
        Plan plan = new Plan("p-1", "tk-1");
        plan.setDagJson(null);

        PlanValidationResult result = validator.validate(plan, new PlanningContext("tk-1", "goal", "t1"));

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("dagJson"));
    }

    @Test
    @DisplayName("dagJson 空串时返回 fail")
    void should_FailWithError_When_DagJsonEmpty() {
        Plan plan = new Plan("p-1", "tk-1");
        plan.setDagJson("");

        PlanValidationResult result = validator.validate(plan, new PlanningContext("tk-1", "goal", "t1"));

        assertThat(result.isPassed()).isFalse();
    }

    @Test
    @DisplayName("单步计划 (node<2) 时通过但产生 atomicity 警告")
    void should_PassWithAtomicityWarning_When_SingleStepPlan() {
        Plan plan = new Plan("p-1", "tk-1");
        plan.setDagJson("{\"node\":\"n1\"}");  // only 1 node

        PlanValidationResult result = validator.validate(plan, new PlanningContext("tk-1", "goal", "t1"));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("atomicity"));
    }

    @Test
    @DisplayName("多步计划无 atomicity 警告")
    void should_PassWithoutAtomicityWarning_When_MultiStepPlan() {
        Plan plan = new Plan("p-1", "tk-1");
        plan.setDagJson("{\"node\":\"n1\"},{\"node\":\"n2\"}");  // 2 nodes

        PlanValidationResult result = validator.validate(plan, new PlanningContext("tk-1", "goal", "t1"));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getWarnings()).noneMatch(w -> w.contains("atomicity"));
    }

    @Test
    @DisplayName("大 DAG (>10000 字符) 产生 efficiency 警告")
    void should_AddEfficiencyWarning_When_DagTooLarge() {
        Plan plan = new Plan("p-1", "tk-1");
        // Build a large DAG with >10000 chars but contains "node" + "retry"
        StringBuilder sb = new StringBuilder("{\"nodes\":[");
        for (int i = 0; i < 1500; i++) {
            sb.append("{\"node\":\"n").append(i).append("\"},");
        }
        sb.append("{}],\"retry\":2}");
        plan.setDagJson(sb.toString());

        PlanValidationResult result = validator.validate(plan, new PlanningContext("tk-1", "goal", "t1"));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("efficiency"));
    }

    @Test
    @DisplayName("cost budget > 5000 产生 cost 警告")
    void should_AddCostWarning_When_BudgetExceedsThreshold() {
        Plan plan = new Plan("p-1", "tk-1");
        plan.setDagJson("{\"node\":\"n1\"},{\"node\":\"n2\"},\"retry\":1");

        PlanningContext ctx = new PlanningContext("tk-1", "goal", "t1");
        ctx.setCostBudgetCent(6000);

        PlanValidationResult result = validator.validate(plan, ctx);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("cost"));
    }

    @Test
    @DisplayName("dagJson 不含 retry 产生 fault-tolerance 警告")
    void should_AddFaultToleranceWarning_When_NoRetryHint() {
        Plan plan = new Plan("p-1", "tk-1");
        plan.setDagJson("{\"node\":\"n1\"},{\"node\":\"n2\"}}");  // no "retry"

        PlanValidationResult result = validator.validate(plan, new PlanningContext("tk-1", "goal", "t1"));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("fault-tolerance"));
    }

    @Test
    @DisplayName("dagJson 含 retry 不产生 fault-tolerance 警告")
    void should_NotAddFaultToleranceWarning_When_RetryHintPresent() {
        Plan plan = new Plan("p-1", "tk-1");
        plan.setDagJson("{\"node\":\"n1\"},{\"node\":\"n2\"},\"retry\":3}");

        PlanValidationResult result = validator.validate(plan, new PlanningContext("tk-1", "goal", "t1"));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getWarnings()).noneMatch(w -> w.contains("fault-tolerance"));
    }

    @Test
    @DisplayName("context null 时仍能通过校验 (跳过 cost 检查)")
    void should_Pass_When_ContextNull() {
        Plan plan = new Plan("p-1", "tk-1");
        plan.setDagJson("{\"node\":\"n1\"},{\"node\":\"n2\"},\"retry\":1");

        PlanValidationResult result = validator.validate(plan, null);

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("返回 5 个 dimensions 列表")
    void should_ReturnFiveDimensions_When_Validated() {
        Plan plan = new Plan("p-1", "tk-1");
        plan.setDagJson("{\"node\":\"n1\"},{\"node\":\"n2\"},\"retry\":1");

        PlanValidationResult result = validator.validate(plan, new PlanningContext("tk-1", "goal", "t1"));

        assertThat(result.getDimensions()).hasSize(5);
        assertThat(result.getDimensions()).contains("completeness", "atomicity", "efficiency", "cost", "fault-tolerance");
    }
}
