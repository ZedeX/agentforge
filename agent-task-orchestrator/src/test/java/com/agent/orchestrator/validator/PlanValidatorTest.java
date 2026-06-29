package com.agent.orchestrator.validator;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PlanValidator 单元测试（对齐 doc 03-task-engine §3.3 Step 5 + PRD 五维度自检）。
 *
 * <p>覆盖 UT-PLAN-009/010 及边界用例：</p>
 * <ul>
 *   <li>UT-PLAN-009: 5 维度全通过 → allPass=true</li>
 *   <li>UT-PLAN-010: 完备性失败 → 抛 PLAN_VALIDATION_FAILED（ErrorCode.COMPLETENESS_FAIL）</li>
 *   <li>边界1: 原子性失败（title 含"和"） → allPass=false</li>
 *   <li>边界2: 效率失败（可并行但被串行） → warnings 非空</li>
 *   <li>边界3: 成本超限 → allPass=false</li>
 *   <li>边界4: 容错失败（R3 写操作无容错配置） → allPass=false</li>
 *   <li>边界5: 空 DAG（无节点）+ 非空 deliverables → 完备性失败</li>
 *   <li>边界6: 单节点 DAG 全通过</li>
 *   <li>边界7: costLimitCent=0 → 成本校验跳过</li>
 *   <li>边界8: deliverables 为空 → 完备性通过</li>
 *   <li>边界9: 容错通过（R3 写节点含 maxRetries + undoAction）→ allPass=true</li>
 *   <li>边界10: validateOrThrow 在成本失败时抛 COST_BUDGET_EXCEEDED</li>
 *   <li>边界11: validateOrThrow 在原子性失败时抛 VALIDATION_FAILED</li>
 * </ul>
 *
 * <p>编码规范：参考 {@code ReplanModeSelectorTest}，使用 AssertJ {@code assertThat}，
 * 中文 {@code @DisplayName}，snake_case 测试方法名 {@code should_X_When_Y}。</p>
 */
class PlanValidatorTest {

    private final PlanValidator validator = new PlanValidator();

    // ============ 节点/边构造辅助方法 ============

    private DagNode node(String nodeId, String nodeType, String title, String outputs) {
        return DagNode.builder()
                .nodeId(nodeId)
                .nodeType(nodeType)
                .title(title)
                .outputs(outputs)
                .status("pending")
                .build();
    }

    private DagEdge dataEdge(String from, String to, String paramMapping) {
        return DagEdge.builder()
                .parentNodeId(from)
                .childNodeId(to)
                .edgeType("DATA")
                .paramMapping(paramMapping)
                .build();
    }

    private ValidationContext context(List<DagNode> nodes, List<DagEdge> edges,
                                      List<String> deliverables,
                                      long costLimitCent, long estimatedCostCent) {
        return ValidationContext.builder()
                .nodes(nodes)
                .edges(edges)
                .deliverables(deliverables)
                .costLimitCent(costLimitCent)
                .estimatedCostCent(estimatedCostCent)
                .maxRetries(2)
                .build();
    }

    // ============ UT-PLAN-009 / UT-PLAN-010 ============

    @Test
    @DisplayName("UT-PLAN-009: 5 维度自检全部通过时 validate() 返回 allPass=true")
    void should_PassValidation_When_AllFiveDimensionsOk() {
        // 5 维度均 pass：
        // - 完备性：deliverables=["report"] 在 n1.outputs 中匹配
        // - 原子性：title="执行查询" 不含连词
        // - 效率：无依赖边
        // - 成本：estimatedCostCent=500 <= 1000*0.8=800
        // - 容错：无 write 节点
        DagNode n1 = node("n1", "execution", "执行查询", "{\"report\":\"monthly\"}");
        ValidationContext ctx = context(
                List.of(n1),
                Collections.emptyList(),
                List.of("report"),
                1000L,
                500L
        );

        ValidationResult result = validator.validate(ctx);

        assertThat(result.isAllPass()).as("5 维度全通过应返回 allPass=true").isTrue();
        assertThat(result.getErrors()).as("全通过时 errors 应为空").isEmpty();
        assertThat(result.getDimensionResults())
                .as("5 个维度结果应全部为 true")
                .allSatisfy((dim, pass) -> assertThat(pass).as(dim.name() + " 应通过").isTrue());
    }

    @Test
    @DisplayName("UT-PLAN-010: 完备性校验失败时 validateOrThrow 抛 PLAN_VALIDATION_FAILED（ErrorCode.COMPLETENESS_FAIL）")
    void should_ReturnPlanValidationFailed_When_CompletenessFailed() {
        // 完备性失败：deliverables=["missingReport"] 在 n1.outputs 中找不到
        DagNode n1 = node("n1", "execution", "执行查询", "{\"x\":1}");
        ValidationContext ctx = context(
                List.of(n1),
                Collections.emptyList(),
                List.of("missingReport"),
                1000L,
                500L
        );

        // 1. validateOrThrow 抛 BusinessException，errorCode=COMPLETENESS_FAIL
        assertThatThrownBy(() -> validator.validateOrThrow(ctx))
                .as("完备性失败应抛 BusinessException(COMPLETENESS_FAIL)")
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .as("errorCode 应为 COMPLETENESS_FAIL")
                                .isEqualTo(ErrorCode.COMPLETENESS_FAIL));

        // 2. validate() 返回 allPass=false，errors 含完备性错误描述
        ValidationResult result = validator.validate(ctx);
        assertThat(result.isAllPass()).as("完备性失败 allPass 应为 false").isFalse();
        assertThat(result.getErrors())
                .as("errors 应包含完备性失败描述")
                .anyMatch(e -> e.contains("完备性"));
        assertThat(result.getDimensionResults().get(ValidationDimension.COMPLETENESS))
                .as("完备性维度结果应为 false")
                .isFalse();
    }

    // ============ 边界用例 ============

    @Test
    @DisplayName("边界1: 节点 title 含\"和\"时原子性校验失败")
    void should_FailAtomicity_When_NodeTitleContainsConjunction() {
        // 原子性失败：n1.title="查询和导出" 含"和"
        DagNode n1 = node("n1", "execution", "查询和导出", "{\"report\":\"x\"}");
        ValidationContext ctx = context(
                List.of(n1),
                Collections.emptyList(),
                List.of("report"),
                1000L,
                500L
        );

        ValidationResult result = validator.validate(ctx);

        assertThat(result.isAllPass()).as("原子性失败 allPass 应为 false").isFalse();
        assertThat(result.getDimensionResults().get(ValidationDimension.ATOMICITY))
                .as("原子性维度应为 false")
                .isFalse();
        assertThat(result.getErrors())
                .as("errors 应包含原子性失败描述")
                .anyMatch(e -> e.contains("原子性"));
    }

    @Test
    @DisplayName("边界2: 存在可并行但被串行的节点对时 warnings 非空")
    void should_AddWarning_When_ParallelizableNodesAreSerialized() {
        // 效率提示：n1->n2 DATA 边无 paramMapping，可调整为 none 并行
        DagNode n1 = node("n1", "execution", "查询订单", "{\"orderId\":1}");
        DagNode n2 = node("n2", "execution", "查询用户", "{\"userId\":1}");
        DagEdge edge = dataEdge("n1", "n2", null); // 无 paramMapping
        ValidationContext ctx = context(
                List.of(n1, n2),
                List.of(edge),
                Collections.emptyList(),
                1000L,
                500L
        );

        ValidationResult result = validator.validate(ctx);

        // 效率维度采用宽松语义：仅 warning 不阻塞 allPass
        assertThat(result.getWarnings())
                .as("效率提示应记录到 warnings")
                .isNotEmpty()
                .anyMatch(w -> w.contains("效率"));
        assertThat(result.getDimensionResults().get(ValidationDimension.EFFICIENCY))
                .as("效率维度采用宽松语义仍记为 pass")
                .isTrue();
    }

    @Test
    @DisplayName("边界3: 预估成本超过 cost_limit_cent × 80% 时成本校验失败")
    void should_FailCost_When_EstimatedCostExceedsThreshold() {
        // 成本超限：costLimitCent=1000, estimatedCostCent=900 > 800
        DagNode n1 = node("n1", "execution", "执行查询", "{\"report\":\"x\"}");
        ValidationContext ctx = context(
                List.of(n1),
                Collections.emptyList(),
                List.of("report"),
                1000L,
                900L
        );

        ValidationResult result = validator.validate(ctx);

        assertThat(result.isAllPass()).as("成本超限 allPass 应为 false").isFalse();
        assertThat(result.getDimensionResults().get(ValidationDimension.COST))
                .as("成本维度应为 false")
                .isFalse();
        assertThat(result.getErrors())
                .as("errors 应包含成本超限描述")
                .anyMatch(e -> e.contains("成本"));
    }

    @Test
    @DisplayName("边界4: R3 写操作节点缺少 maxRetries/undoAction 时容错校验失败")
    void should_FailFaultTolerance_When_WriteNodeMissingConfig() {
        // 容错失败：n1.nodeType="execution_write" 但 outputs="{}" 无容错配置
        DagNode n1 = node("n1", "execution_write", "写入订单", "{\"order\":1}");
        ValidationContext ctx = context(
                List.of(n1),
                Collections.emptyList(),
                Collections.emptyList(),
                0L,
                0L
        );

        ValidationResult result = validator.validate(ctx);

        assertThat(result.isAllPass()).as("容错失败 allPass 应为 false").isFalse();
        assertThat(result.getDimensionResults().get(ValidationDimension.FAULT_TOLERANCE))
                .as("容错维度应为 false")
                .isFalse();
        assertThat(result.getErrors())
                .as("errors 应包含容错失败描述")
                .anyMatch(e -> e.contains("容错"));
    }

    @Test
    @DisplayName("边界5: 空 DAG（无节点）+ 非空 deliverables 时完备性失败")
    void should_FailCompleteness_When_NodesEmptyButDeliverablesNonEmpty() {
        ValidationContext ctx = context(
                Collections.emptyList(),
                Collections.emptyList(),
                List.of("report"),
                1000L,
                500L
        );

        ValidationResult result = validator.validate(ctx);

        assertThat(result.isAllPass()).as("空 DAG + 非空 deliverables allPass 应为 false").isFalse();
        assertThat(result.getDimensionResults().get(ValidationDimension.COMPLETENESS))
                .as("完备性维度应为 false")
                .isFalse();
        assertThat(result.getErrors())
                .as("errors 应提示节点为空")
                .anyMatch(e -> e.contains("节点为空"));
    }

    @Test
    @DisplayName("边界6: 单节点 DAG 且无完备性要求时全部维度通过")
    void should_PassValidation_When_SingleNodeAndNoDeliverables() {
        // 单节点 + deliverables 为空 + costLimitCent=0 → 全维度通过
        DagNode n1 = node("n1", "execution", "执行查询", "{}");
        ValidationContext ctx = context(
                List.of(n1),
                Collections.emptyList(),
                Collections.emptyList(),
                0L,
                0L
        );

        ValidationResult result = validator.validate(ctx);

        assertThat(result.isAllPass()).as("单节点全通过 allPass 应为 true").isTrue();
        assertThat(result.getErrors()).as("单节点全通过 errors 应为空").isEmpty();
    }

    @Test
    @DisplayName("边界7: costLimitCent=0 时成本校验跳过（视为通过）")
    void should_SkipCost_When_CostLimitIsZero() {
        // costLimitCent=0 → 成本校验跳过；estimatedCostCent=99999 也不应失败
        DagNode n1 = node("n1", "execution", "执行查询", "{\"report\":\"x\"}");
        ValidationContext ctx = context(
                List.of(n1),
                Collections.emptyList(),
                List.of("report"),
                0L,
                99999L
        );

        ValidationResult result = validator.validate(ctx);

        assertThat(result.getDimensionResults().get(ValidationDimension.COST))
                .as("costLimitCent=0 时成本维度应跳过（视为通过）")
                .isTrue();
        assertThat(result.isAllPass()).as("全维度应通过").isTrue();
    }

    @Test
    @DisplayName("边界8: deliverables 为空时完备性校验通过")
    void should_PassCompleteness_When_DeliverablesEmpty() {
        // deliverables=[] → 完备性通过；nodes 任意
        DagNode n1 = node("n1", "execution", "执行查询", "{}");
        ValidationContext ctx = context(
                List.of(n1),
                Collections.emptyList(),
                Collections.emptyList(),
                1000L,
                500L
        );

        ValidationResult result = validator.validate(ctx);

        assertThat(result.getDimensionResults().get(ValidationDimension.COMPLETENESS))
                .as("deliverables 为空时完备性应通过")
                .isTrue();
        assertThat(result.isAllPass()).as("全维度应通过").isTrue();
    }

    @Test
    @DisplayName("边界9: R3 写操作节点 outputs 含 maxRetries 与 undoAction 时容错通过")
    void should_PassFaultTolerance_When_WriteNodeHasCompensationConfig() {
        // 容错通过：outputs 含 maxRetries 与 undoAction 字段名
        DagNode n1 = node("n1", "execution_write", "写入订单",
                "{\"order\":1,\"maxRetries\":3,\"undoAction\":\"rollback_order\"}");
        ValidationContext ctx = context(
                List.of(n1),
                Collections.emptyList(),
                Collections.emptyList(),
                0L,
                0L
        );

        ValidationResult result = validator.validate(ctx);

        assertThat(result.getDimensionResults().get(ValidationDimension.FAULT_TOLERANCE))
                .as("R3 写节点配置 maxRetries/undoAction 后容错维度应通过")
                .isTrue();
        assertThat(result.isAllPass()).as("全维度应通过").isTrue();
    }

    @Test
    @DisplayName("边界10: validateOrThrow 在成本超限时抛 COST_BUDGET_EXCEEDED")
    void should_ThrowCostBudgetExceeded_When_CostExceedsLimit() {
        DagNode n1 = node("n1", "execution", "执行查询", "{\"report\":\"x\"}");
        ValidationContext ctx = context(
                List.of(n1),
                Collections.emptyList(),
                List.of("report"),
                1000L,
                900L
        );

        assertThatThrownBy(() -> validator.validateOrThrow(ctx))
                .as("成本超限应抛 BusinessException(COST_BUDGET_EXCEEDED)")
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .as("errorCode 应为 COST_BUDGET_EXCEEDED")
                                .isEqualTo(ErrorCode.COST_BUDGET_EXCEEDED));
    }

    @Test
    @DisplayName("边界11: validateOrThrow 在原子性失败时抛 VALIDATION_FAILED（非完备性非成本场景）")
    void should_ThrowValidationFailed_When_AtomicityFailed() {
        // 原子性失败：title 含"和"
        DagNode n1 = node("n1", "execution", "查询和导出", "{\"report\":\"x\"}");
        ValidationContext ctx = context(
                List.of(n1),
                Collections.emptyList(),
                List.of("report"),
                1000L,
                500L
        );

        assertThatThrownBy(() -> validator.validateOrThrow(ctx))
                .as("原子性失败应抛 BusinessException(VALIDATION_FAILED)")
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .as("errorCode 应为 VALIDATION_FAILED（PLAN_VALIDATION_FAILED）")
                                .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("边界12: 多维度同时失败时 errors 包含全部失败维度描述")
    void should_AggregateErrors_When_MultipleDimensionsFail() {
        // 同时触发：完备性失败（deliverables 不匹配） + 原子性失败（title 含"和"） + 容错失败（写节点无容错）
        DagNode n1 = node("n1", "execution_write", "查询和导出", "{\"order\":1}");
        ValidationContext ctx = context(
                List.of(n1),
                Collections.emptyList(),
                List.of("missingReport"),
                1000L,
                900L // 成本也超限
        );

        ValidationResult result = validator.validate(ctx);

        assertThat(result.isAllPass()).as("多维度失败 allPass 应为 false").isFalse();
        assertThat(result.getErrors())
                .as("errors 应包含完备性/原子性/成本/容错 4 类失败描述")
                .anyMatch(e -> e.contains("完备性"))
                .anyMatch(e -> e.contains("原子性"))
                .anyMatch(e -> e.contains("成本"))
                .anyMatch(e -> e.contains("容错"));
    }

    @Test
    @DisplayName("边界13: null 上下文时抛 BusinessException(PARAM_INVALID)")
    void should_ThrowParamInvalid_When_ContextIsNull() {
        assertThatThrownBy(() -> validator.validate(null))
                .as("null 上下文应抛 BusinessException(PARAM_INVALID)")
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .as("errorCode 应为 PARAM_INVALID")
                                .isEqualTo(ErrorCode.PARAM_INVALID));
    }
}
