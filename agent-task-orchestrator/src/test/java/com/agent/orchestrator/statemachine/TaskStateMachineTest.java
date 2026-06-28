package com.agent.orchestrator.statemachine;

import com.agent.common.constant.TaskStatus;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void canTransitTo_pendingToPlanning_returnsTrue() {
        assertTrue(stateMachine.canTransitTo(TaskStatus.PENDING, TaskStatus.PLANNING),
                "PENDING → PLANNING 应合法");
    }

    @Test
    void canTransitTo_planningToRunning_returnsTrue() {
        assertTrue(stateMachine.canTransitTo(TaskStatus.PLANNING, TaskStatus.RUNNING),
                "PLANNING → RUNNING 应合法");
    }

    @Test
    void canTransitTo_runningToSubtaskRunning_returnsTrue() {
        assertTrue(stateMachine.canTransitTo(TaskStatus.RUNNING, TaskStatus.SUBTASK_RUNNING),
                "RUNNING → SUBTASK_RUNNING 应合法");
    }

    @Test
    void canTransitTo_subtaskRunningToSuccess_returnsTrue() {
        assertTrue(stateMachine.canTransitTo(TaskStatus.SUBTASK_RUNNING, TaskStatus.SUCCESS),
                "SUBTASK_RUNNING → SUCCESS 应合法");
    }

    @Test
    void canTransitTo_waitingHumanToReplanning_returnsTrue() {
        assertTrue(stateMachine.canTransitTo(TaskStatus.WAITING_HUMAN, TaskStatus.REPLANNING),
                "WAITING_HUMAN → REPLANNING（人工触发）应合法");
    }

    @Test
    void canTransitTo_replanningToSubtaskRunning_returnsTrue() {
        assertTrue(stateMachine.canTransitTo(TaskStatus.REPLANNING, TaskStatus.SUBTASK_RUNNING),
                "REPLANNING → SUBTASK_RUNNING（增量完成继续跑）应合法");
    }

    // ===== 非法转换 =====

    @Test
    void canTransitTo_successToRunning_returnsFalse() {
        assertFalse(stateMachine.canTransitTo(TaskStatus.SUCCESS, TaskStatus.RUNNING),
                "SUCCESS 为终态，禁止流转到 RUNNING");
    }

    @Test
    void canTransitTo_pendingToSuccess_returnsFalse() {
        assertFalse(stateMachine.canTransitTo(TaskStatus.PENDING, TaskStatus.SUCCESS),
                "PENDING → SUCCESS 跳阶段，禁止");
    }

    @Test
    void canTransitTo_planningToSubtaskRunning_returnsFalse() {
        assertFalse(stateMachine.canTransitTo(TaskStatus.PLANNING, TaskStatus.SUBTASK_RUNNING),
                "PLANNING → SUBTASK_RUNNING 跳过 RUNNING，禁止");
    }

    @Test
    void canTransitTo_successToSelf_returnsFalse() {
        assertFalse(stateMachine.canTransitTo(TaskStatus.SUCCESS, TaskStatus.SUCCESS),
                "SUCCESS 为终态，自环禁止");
    }

    // ===== transit 方法 =====

    @Test
    void transit_legalTransition_returnsTargetStatus() {
        TaskStatus result = stateMachine.transit(TaskStatus.PENDING, TaskStatus.PLANNING);
        assertEquals(TaskStatus.PLANNING, result, "合法转换应返回目标状态");
    }

    @Test
    void transit_illegalTransition_throwsBusinessExceptionWithParamInvalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.transit(TaskStatus.SUCCESS, TaskStatus.RUNNING),
                "非法转换应抛 BusinessException");
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode(),
                "非法状态转换错误码应为 PARAM_INVALID");
    }
}
