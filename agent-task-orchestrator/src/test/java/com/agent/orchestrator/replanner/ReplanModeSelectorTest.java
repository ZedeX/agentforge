package com.agent.orchestrator.replanner;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ReplanModeSelector 单元测试（对齐 doc 03-task-engine §5.1/§5.3 + F5.D2/D3/D4 决策节点）。
 *
 * <p>覆盖 6 类场景：</p>
 * <ul>
 *   <li>UT-ORCH-009: 单子任务失败 + 其余有效 → INCREMENTAL</li>
 *   <li>UT-ORCH-010: 需求变更 → FULL</li>
 *   <li>UT-ORCH-011: 重规划次数超限 → 抛 REPLAN_EXHAUSTED / 返回 ABORT</li>
 *   <li>全部子任务失败 → FULL</li>
 *   <li>多个失败但其余有效 → INCREMENTAL</li>
 *   <li>UT-ORCH-013: 失败过半 → FULL</li>
 * </ul>
 */
class ReplanModeSelectorTest {

    private final ReplanModeSelector selector = new ReplanModeSelector();

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

    @Test
    @DisplayName("UT-ORCH-009: 单子任务失败且其余输出有效时触发增量重规划")
    void should_TriggerIncrementalReplan_When_SingleSubtaskFails() {
        // failed_count=1, other_outputs_valid=true → INCREMENTAL
        ReplanModeSelector.ReplanContext ctx = context(1, 5, true, "subtask_failed", 0, 2);

        ReplanMode mode = selector.select(ctx);

        assertThat(mode).as("单子任务失败 + 其余有效应触发增量重规划").isEqualTo(ReplanMode.INCREMENTAL);
    }

    @Test
    @DisplayName("UT-ORCH-010: 需求变更影响根节点时选择全量重规划")
    void should_SelectFullReplan_When_RequirementChangedAtRoot() {
        // reason=requirement_change → FULL
        ReplanModeSelector.ReplanContext ctx = context(0, 5, true,
                ReplanModeSelector.REASON_REQUIREMENT_CHANGE, 0, 2);

        ReplanMode mode = selector.select(ctx);

        assertThat(mode).as("需求变更应触发全量重规划").isEqualTo(ReplanMode.FULL);
    }

    @Test
    @DisplayName("UT-ORCH-011: 重规划次数超过上限时 select 应抛 REPLAN_EXHAUSTED")
    void should_ThrowReplanExhausted_When_ReplanCountExceedsMax() {
        // replan_count=3, max_replan=2 → 抛 REPLAN_EXHAUSTED
        ReplanModeSelector.ReplanContext ctx = context(1, 5, true, "subtask_failed", 3, 2);

        assertThatThrownBy(() -> selector.select(ctx))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REPLAN_EXHAUSTED));
    }

    @Test
    @DisplayName("UT-ORCH-011: 重规划次数超过上限时 selectOrAbort 应返回 ABORT（转 WAITING_HUMAN）")
    void should_ReturnAbort_When_ReplanCountExceedsMax() {
        // replan_count=3, max_replan=2 → 返回 ABORT
        ReplanModeSelector.ReplanContext ctx = context(1, 5, true, "subtask_failed", 3, 2);

        ReplanMode mode = selector.selectOrAbort(ctx);

        assertThat(mode).as("重规划次数耗尽应返回 ABORT 以便转人工介入").isEqualTo(ReplanMode.ABORT);
    }

    @Test
    @DisplayName("全部子任务失败时选择全量重规划")
    void should_SelectFullReplan_When_AllSubtasksFail() {
        // failed_count=total_count → FULL
        ReplanModeSelector.ReplanContext ctx = context(5, 5, false, "all_failed", 0, 2);

        ReplanMode mode = selector.select(ctx);

        assertThat(mode).as("全部子任务失败应触发全量重规划").isEqualTo(ReplanMode.FULL);
    }

    @Test
    @DisplayName("多个子任务失败但其余输出有效时选择增量重规划")
    void should_SelectIncrementalReplan_When_MultipleSubtasksFailButOthersValid() {
        // failed_count=2, total=5, other_outputs_valid=true → INCREMENTAL（未过半）
        ReplanModeSelector.ReplanContext ctx = context(2, 5, true, "subtask_failed", 0, 2);

        ReplanMode mode = selector.select(ctx);

        assertThat(mode).as("少数失败 + 其余有效应触发增量重规划").isEqualTo(ReplanMode.INCREMENTAL);
    }

    @Test
    @DisplayName("UT-ORCH-013: 失败过半时选择全量重规划")
    void should_SelectFullReplan_When_MajoritySubtasksFail() {
        // 5 子任务中 3 个失败（过半），其余有效 → FULL
        ReplanModeSelector.ReplanContext ctx = context(3, 5, true, "subtask_failed", 0, 2);

        ReplanMode mode = selector.select(ctx);

        assertThat(mode).as("失败过半应判定为致命异常触发全量重规划").isEqualTo(ReplanMode.FULL);
    }

    @Test
    @DisplayName("少数失败但其余输出无效时选择全量重规划")
    void should_SelectFullReplan_When_OtherOutputsInvalid() {
        // failed_count=1, other_outputs_valid=false → FULL
        ReplanModeSelector.ReplanContext ctx = context(1, 5, false, "subtask_failed", 0, 2);

        ReplanMode mode = selector.select(ctx);

        assertThat(mode).as("其余输出无效应触发全量重规划").isEqualTo(ReplanMode.FULL);
    }

    @Test
    @DisplayName("replanCount 等于 maxReplan 时仍允许重规划（边界条件）")
    void should_AllowReplan_When_ReplanCountEqualsMax() {
        // 边界：replan_count == max_replan 时仍未超限
        ReplanModeSelector.ReplanContext ctx = context(1, 5, true, "subtask_failed", 2, 2);

        ReplanMode mode = selector.select(ctx);

        assertThat(mode).as("达到上限但未超出时应允许重规划").isEqualTo(ReplanMode.INCREMENTAL);
    }

    @Test
    @DisplayName("未指定触发原因且少数失败 + 其余有效时仍选择增量重规划")
    void should_SelectIncrementalReplan_When_TriggerReasonIsNull() {
        ReplanModeSelector.ReplanContext ctx = context(1, 4, true, null, 0, 2);

        ReplanMode mode = selector.select(ctx);

        assertThat(mode).isEqualTo(ReplanMode.INCREMENTAL);
    }

    @Test
    @DisplayName("需求变更且重规划次数已超限时仍优先抛 REPLAN_EXHAUSTED（熔断优先）")
    void should_ThrowReplanExhausted_When_RequirementChangeButReplanExhausted() {
        ReplanModeSelector.ReplanContext ctx = context(0, 5, true,
                ReplanModeSelector.REASON_REQUIREMENT_CHANGE, 5, 2);

        assertThatThrownBy(() -> selector.select(ctx))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REPLAN_EXHAUSTED));
    }
}
