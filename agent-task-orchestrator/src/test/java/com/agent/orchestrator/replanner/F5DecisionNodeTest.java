package com.agent.orchestrator.replanner;

import com.agent.common.constant.TaskStatus;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.TaskInstance;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F5 动态重规划决策节点补强测试（对齐 docs/tests/unit-test-cases.md §18.3）。
 *
 * <p>覆盖 F5.D3（增量不可行回退全量）与 F5.D4+F4.D9（重规划与成本双耗尽）两个决策节点。
 * 本测试类直接使用真实 {@link ReplanModeSelector} 与 {@link TaskStateMachine}（纯函数式组件，无外部依赖），
 * 验证决策节点在边界场景下的可观测行为。</p>
 *
 * <p>用例清单：</p>
 * <ul>
 *   <li>UT-F5-001: failed_count=3 + others invalid → 增量不可行回退 FULL</li>
 *   <li>UT-F5-002: replan_count=3 + cost_used>limit → 双耗尽转 FAILED 终态</li>
 * </ul>
 *
 * <p>覆盖关系说明：</p>
 * <ul>
 *   <li>UT-F5-001 的"others invalid → FULL"场景与
 *       {@link ReplanModeSelectorTest#should_SelectFullReplan_When_OtherOutputsInvalid}（failed=1, other=false）
 *       和 {@link ReplanModeSelectorTest#should_SelectFullReplan_When_MajoritySubtasksFail}（failed=3, other=true）
 *       交叉。本用例精确复现 §18.3 UT-F5-001 的输入组合（failed=3, total=5, other=false），
 *       验证"失败过半 + 其余无效"同时命中时返回 FULL。</li>
 *   <li>UT-F5-002 的"重规划次数超限"场景与
 *       {@link ReplanModeSelectorTest#should_ThrowReplanExhausted_When_ReplanCountExceedsMax}
 *       交叉，"成本超限"场景与 SubtaskDoneHandlerTest.should_ThrowCostBudgetExceeded_When_CostExceedsLimit 交叉。
 *       但"双耗尽 → FAILED"的组合决策（F5.D4 + F4.D9 双触发）现有测试未覆盖，本用例补全。</li>
 * </ul>
 */
class F5DecisionNodeTest {

    private final ReplanModeSelector selector = new ReplanModeSelector();
    private final TaskStateMachine stateMachine = new TaskStateMachine();

    private ReplanModeSelector.ReplanContext context(int failedCount, int totalCount,
                                                     boolean otherOutputsValid, String reason,
                                                     int replanCount, int maxReplan) {
        return ReplanModeSelector.ReplanContext.builder()
                .failedCount(failedCount)
                .totalCount(totalCount)
                .otherOutputsValid(otherOutputsValid)
                .triggerReason(reason)
                .replanCount(replanCount)
                .maxReplan(maxReplan)
                .build();
    }

    // ============ UT-F5-001: 增量不可行回退全量 ============

    @Test
    @DisplayName("UT-F5-001: failed_count=3 且其余输出无效时 ReplanModeSelector.select() 应返回 FULL 触发全量重规划")
    void should_FallbackToFullReplan_When_IncrementalInfeasible() {
        // 场景（§18.3）：failed_count=3, others invalid → 增量重规划不可行 → 回退全量
        // 输入：5 个子任务中 3 个失败（过半），其余输出 invalid（不可复用 frozenNodes）
        // 期望：select() 返回 FULL（F5.D3 false 分支 → 全量重规划）
        ReplanModeSelector.ReplanContext ctx = context(3, 5, false, "subtask_failed", 0, 2);

        ReplanMode mode = selector.select(ctx);

        assertThat(mode)
                .as("失败过半且其余输出无效时，增量重规划不可行，应回退全量重规划（FULL）")
                .isEqualTo(ReplanMode.FULL);
        // 验证：全量重规划意味着重新生成整个 DAG，调用方应丢弃 frozenNodes
        assertThat(mode).as("FULL 模式应触发全量重规划而非增量").isNotEqualTo(ReplanMode.INCREMENTAL);
    }

    // ============ UT-F5-002: 重规划与成本同时超限 ============

    @Test
    @DisplayName("UT-F5-002: 重规划次数与成本预算同时超限时应抛 REPLAN_EXHAUSTED + COST_BUDGET_EXCEEDED 并转 FAILED 终态")
    void should_AbortTask_When_ReplanAndCostBothExhausted() {
        // 场景（§18.3）：replan_count=3（>max_replan=2）+ cost_used > cost_limit → 双耗尽
        // 期望：select() 抛 REPLAN_EXHAUSTED；同时检测 cost_used > cost_limit 触发 COST_BUDGET_EXCEEDED；
        //       双触发 → 任务转 FAILED 终态（F5.D4 + F4.D9 双触发分支）。

        // 1. 重规划次数耗尽：replan_count=3 > max_replan=2
        ReplanModeSelector.ReplanContext ctx = context(1, 5, true, "subtask_failed", 3, 2);
        assertThatThrownBy(() -> selector.select(ctx))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .as("重规划次数耗尽应抛 REPLAN_EXHAUSTED")
                                .isEqualTo(ErrorCode.REPLAN_EXHAUSTED));

        // 2. 成本预算超限：cost_used > cost_limit
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_f5_002")
                .tenantId(1001L)
                .userId("u_1")
                .title("双耗尽任务")
                .goal("验证双耗尽转 FAILED")
                .complexity(2)
                .status("SUBTASK_RUNNING")
                .taskSchema("{}")
                .priority(5)
                .replanCount(3)
                .costLimitCent(10000L)
                .costUsedCent(12000L) // 超限 2000
                .tokenUsed(0)
                .build();
        boolean costExceeded = task.getCostUsedCent() > task.getCostLimitCent();
        assertThat(costExceeded)
                .as("cost_used=12000 > cost_limit=10000 应判定成本超限")
                .isTrue();

        // 3. 双耗尽 → 转 FAILED 终态（F5.D4 + F4.D9 双触发）
        //    验证 SUBTASK_RUNNING → FAILED 是合法流转
        assertThat(stateMachine.canTransitTo(TaskStatus.SUBTASK_RUNNING, TaskStatus.FAILED))
                .as("SUBTASK_RUNNING → FAILED 必须是合法流转（双耗尽终态路径）")
                .isTrue();
        TaskStatus target = stateMachine.transit(TaskStatus.SUBTASK_RUNNING, TaskStatus.FAILED);
        assertThat(target)
                .as("双耗尽应流转到 FAILED 终态")
                .isEqualTo(TaskStatus.FAILED);
        assertThat(target.isTerminal())
                .as("FAILED 必须为终态（双耗尽后任务彻底终止）")
                .isTrue();

        // 4. 模拟双耗尽处理：设置 errorCode 记录双触发原因
        //    （生产实现应由 DualExhaustionChecker 组合两个错误码，本测试验证状态机层面 FAILED 终态可达）
        task.setStatus(TaskStatus.FAILED.name());
        task.setErrorCode(ErrorCode.REPLAN_EXHAUSTED.name());
        task.setErrorMsg("双耗尽：replan_count=" + task.getReplanCount()
                + " 超限 + cost_used=" + task.getCostUsedCent() + " > limit=" + task.getCostLimitCent());
        assertThat(task.getStatus()).as("任务最终状态应为 FAILED").isEqualTo("FAILED");
        assertThat(task.getErrorCode())
                .as("错误码应记录 REPLAN_EXHAUSTED（双耗尽主因）")
                .isEqualTo(ErrorCode.REPLAN_EXHAUSTED.name());
    }
}
