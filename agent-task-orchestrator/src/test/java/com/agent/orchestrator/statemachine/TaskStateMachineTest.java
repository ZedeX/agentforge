package com.agent.orchestrator.statemachine;

import com.agent.common.constant.TaskStatus;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TaskStateMachine 单元测试（Red 阶段：实现尚未存在，预期编译失败）。
 *
 * <p>对齐 doc 03-task-engine §6.2 合法状态流转矩阵（10 状态机）。
 * 测试覆盖：</p>
 * <ul>
 *   <li>合法转换：PENDING→PLANNING / PLANNING→RUNNING / RUNNING→SUBTASK_RUNNING /
 *       SUBTASK_RUNNING→SUCCESS / WAITING_HUMAN→REPLANNING / REPLANNING→SUBTASK_RUNNING</li>
 *   <li>非法转换：SUCCESS→RUNNING（终态禁止流转）/ PENDING→SUCCESS（跳阶段）/
 *       PLANNING→SUBTASK_RUNNING（跳过 RUNNING）/ SUCCESS→SUCCESS（终态自环）</li>
 *   <li>transit 合法路径返回目标状态，非法路径抛 BusinessException(PARAM_INVALID)</li>
 * </ul>
 */
class TaskStateMachineTest {

    private final TaskStateMachine stateMachine = new TaskStateMachine();

    // ===== 合法转换 =====

    @Test
    @DisplayName("PENDING → PLANNING 应为合法状态流转")
    void should_ReturnTrue_When_TransitFromPendingToPlanning() {
        assertThat(stateMachine.canTransitTo(TaskStatus.PENDING, TaskStatus.PLANNING))
                .as("PENDING → PLANNING 应合法").isTrue();
    }

    @Test
    @DisplayName("PLANNING → RUNNING 应为合法状态流转")
    void should_ReturnTrue_When_TransitFromPlanningToRunning() {
        assertThat(stateMachine.canTransitTo(TaskStatus.PLANNING, TaskStatus.RUNNING))
                .as("PLANNING → RUNNING 应合法").isTrue();
    }

    @Test
    @DisplayName("RUNNING → SUBTASK_RUNNING 应为合法状态流转")
    void should_ReturnTrue_When_TransitFromRunningToSubtaskRunning() {
        assertThat(stateMachine.canTransitTo(TaskStatus.RUNNING, TaskStatus.SUBTASK_RUNNING))
                .as("RUNNING → SUBTASK_RUNNING 应合法").isTrue();
    }

    @Test
    @DisplayName("SUBTASK_RUNNING → SUCCESS 应为合法状态流转")
    void should_ReturnTrue_When_TransitFromSubtaskRunningToSuccess() {
        assertThat(stateMachine.canTransitTo(TaskStatus.SUBTASK_RUNNING, TaskStatus.SUCCESS))
                .as("SUBTASK_RUNNING → SUCCESS 应合法").isTrue();
    }

    @Test
    @DisplayName("WAITING_HUMAN → REPLANNING 应为合法状态流转（人工触发）")
    void should_ReturnTrue_When_TransitFromWaitingHumanToReplanning() {
        assertThat(stateMachine.canTransitTo(TaskStatus.WAITING_HUMAN, TaskStatus.REPLANNING))
                .as("WAITING_HUMAN → REPLANNING（人工触发）应合法").isTrue();
    }

    @Test
    @DisplayName("REPLANNING → SUBTASK_RUNNING 应为合法状态流转（增量完成继续跑）")
    void should_ReturnTrue_When_TransitFromReplanningToSubtaskRunning() {
        assertThat(stateMachine.canTransitTo(TaskStatus.REPLANNING, TaskStatus.SUBTASK_RUNNING))
                .as("REPLANNING → SUBTASK_RUNNING（增量完成继续跑）应合法").isTrue();
    }

    // ===== 非法转换 =====

    @Test
    @DisplayName("SUCCESS 为终态，禁止流转到 RUNNING")
    void should_ReturnFalse_When_TransitFromSuccessToRunning() {
        assertThat(stateMachine.canTransitTo(TaskStatus.SUCCESS, TaskStatus.RUNNING))
                .as("SUCCESS 为终态，禁止流转到 RUNNING").isFalse();
    }

    @Test
    @DisplayName("PENDING → SUCCESS 跳阶段，禁止")
    void should_ReturnFalse_When_TransitFromPendingToSuccess() {
        assertThat(stateMachine.canTransitTo(TaskStatus.PENDING, TaskStatus.SUCCESS))
                .as("PENDING → SUCCESS 跳阶段，禁止").isFalse();
    }

    @Test
    @DisplayName("PLANNING → SUBTASK_RUNNING 跳过 RUNNING，禁止")
    void should_ReturnFalse_When_TransitFromPlanningToSubtaskRunning() {
        assertThat(stateMachine.canTransitTo(TaskStatus.PLANNING, TaskStatus.SUBTASK_RUNNING))
                .as("PLANNING → SUBTASK_RUNNING 跳过 RUNNING，禁止").isFalse();
    }

    @Test
    @DisplayName("SUCCESS 为终态，自环禁止")
    void should_ReturnFalse_When_TransitFromSuccessToSelf() {
        assertThat(stateMachine.canTransitTo(TaskStatus.SUCCESS, TaskStatus.SUCCESS))
                .as("SUCCESS 为终态，自环禁止").isFalse();
    }

    // ===== transit 方法 =====

    @Test
    @DisplayName("合法状态转换应返回目标状态")
    void should_ReturnTargetStatus_When_TransitionIsLegal() {
        TaskStatus result = stateMachine.transit(TaskStatus.PENDING, TaskStatus.PLANNING);
        assertThat(result).as("合法转换应返回目标状态").isEqualTo(TaskStatus.PLANNING);
    }

    @Test
    @DisplayName("非法状态转换应抛出 BusinessException 且错误码为 PARAM_INVALID")
    void should_ThrowBusinessExceptionWithParamInvalid_When_TransitionIsIllegal() {
        assertThatThrownBy(() -> stateMachine.transit(TaskStatus.SUCCESS, TaskStatus.RUNNING))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }
}
