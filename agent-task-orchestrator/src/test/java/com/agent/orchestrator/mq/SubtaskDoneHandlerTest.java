package com.agent.orchestrator.mq;

import com.agent.common.constant.TaskStatus;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.TaskInstance;
import com.agent.orchestrator.mq.event.SubtaskDoneEvent;
import com.agent.orchestrator.repository.TaskInstanceRepository;
import com.agent.orchestrator.replanner.ReplanModeSelector;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SubtaskDoneHandler 单元测试（对齐 doc 03-task-engine §7.4 回调伪代码）。
 *
 * <p>覆盖 10 个核心场景（UT-MQ-001~010）：</p>
 * <ul>
 *   <li>UT-MQ-001: 幂等校验（重复 eventId 跳过）</li>
 *   <li>UT-MQ-002: 任务不存在抛 TASK_NOT_FOUND</li>
 *   <li>UT-MQ-003: 成本累加</li>
 *   <li>UT-MQ-004: 成本超限转 TIMEOUT + 抛 COST_BUDGET_EXCEEDED</li>
 *   <li>UT-MQ-005: token 累加</li>
 *   <li>UT-MQ-006: success 分支不触发状态流转</li>
 *   <li>UT-MQ-007: failed + MAX_RETRY_EXCEEDED → REPLANNING</li>
 *   <li>UT-MQ-008: failed + AGENT_NOT_FOUND → WAITING_HUMAN</li>
 *   <li>UT-MQ-009: failed + 未知错误码 → WAITING_HUMAN（默认）</li>
 *   <li>UT-MQ-010: require_review → WAITING_HUMAN</li>
 * </ul>
 *
 * <p>使用 {@link Strictness#LENIENT} 避免跨用例共享 mock 时的 UnnecessaryStubbingException。
 * 方法命名遵循 {@code should_{期望}_When_{条件}} snake_case 风格。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubtaskDoneHandlerTest {

    @Mock private TaskInstanceRepository repository;
    @Mock private TaskStateMachine stateMachine;
    @Mock private ReplanModeSelector replanModeSelector;
    @InjectMocks private SubtaskDoneHandler handler;

    /** 构造一个处于 SUBTASK_RUNNING 状态、成本/token 清零的 TaskInstance。 */
    private TaskInstance task(String taskId, long costUsed, long costLimit, int tokenUsed) {
        return TaskInstance.builder()
                .taskId(taskId)
                .tenantId(1001L)
                .userId("u_1")
                .title("测试任务")
                .goal("测试目标")
                .complexity(2)
                .status("SUBTASK_RUNNING")
                .taskSchema("{}")
                .priority(5)
                .replanCount(0)
                .costLimitCent(costLimit)
                .costUsedCent(costUsed)
                .tokenUsed(tokenUsed)
                .build();
    }

    private SubtaskDoneEvent doneEvent(String eventId, String taskId, String status,
                                       Long costCent, Integer tokenUsed,
                                       String errorCode) {
        return SubtaskDoneEvent.builder()
                .eventId(eventId)
                .taskId(taskId)
                .subtaskId("st_1")
                .nodeId("n_1")
                .status(status)
                .costCent(costCent)
                .tokenUsed(tokenUsed)
                .errorCode(errorCode)
                .build();
    }

    @BeforeEach
    void setUp() {
        // 默认：stateMachine.transit 不抛异常（合法转换）
        // mock 默认 void/return 行为已足够，无需显式 stub
    }

    // ============ UT-MQ-001: 幂等校验 ============

    @Test
    @DisplayName("UT-MQ-001: 重复事件应被幂等跳过，不执行任何状态流转或持久化")
    void should_SkipProcessing_When_EventAlreadyConsumed() {
        TaskInstance task = task("tk_001", 0L, 10000L, 0);
        when(repository.findByTaskId("tk_001")).thenReturn(Optional.of(task));
        SubtaskDoneEvent event = doneEvent("ev_dup", "tk_001", "success", 100L, 50, null);

        handler.handle(event);  // 首次消费
        handler.handle(event);  // 重复消费

        verify(repository, times(1)).findByTaskId("tk_001");
        verify(repository, times(1)).save(any());
        verify(stateMachine, never()).transit(any(), any());
    }

    // ============ UT-MQ-002: 任务不存在 ============

    @Test
    @DisplayName("UT-MQ-002: 任务不存在时应抛 TASK_NOT_FOUND 业务异常")
    void should_ThrowTaskNotFound_When_TaskNotExists() {
        when(repository.findByTaskId("tk_missing")).thenReturn(Optional.empty());
        SubtaskDoneEvent event = doneEvent("ev_002", "tk_missing", "success", null, null, null);

        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TASK_NOT_FOUND));
        verify(repository, never()).save(any());
    }

    // ============ UT-MQ-003: 成本累加 ============

    @Test
    @DisplayName("UT-MQ-003: 子任务成功上报成本应累加到 task.costUsedCent")
    void should_AccumulateCost_When_SubtaskSucceedsWithCost() {
        TaskInstance task = task("tk_003", 500L, 10000L, 0);
        when(repository.findByTaskId("tk_003")).thenReturn(Optional.of(task));
        SubtaskDoneEvent event = doneEvent("ev_003", "tk_003", "success", 300L, null, null);

        handler.handle(event);

        assertThat(task.getCostUsedCent()).isEqualTo(800L);
        verify(repository).save(task);
    }

    // ============ UT-MQ-004: 成本超限 ============

    @Test
    @DisplayName("UT-MQ-004: 成本累加后超限应转 TIMEOUT 并抛 COST_BUDGET_EXCEEDED")
    void should_ThrowCostBudgetExceeded_When_CostExceedsLimit() {
        TaskInstance task = task("tk_004", 9000L, 10000L, 0);
        when(repository.findByTaskId("tk_004")).thenReturn(Optional.of(task));
        SubtaskDoneEvent event = doneEvent("ev_004", "tk_004", "success", 2000L, null, null);

        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COST_BUDGET_EXCEEDED));

        // 验证状态流转到 TIMEOUT（含 finishedAt 设置）
        verify(stateMachine).transit(eq(TaskStatus.SUBTASK_RUNNING), eq(TaskStatus.TIMEOUT));
        assertThat(task.getStatus()).isEqualTo("TIMEOUT");
        assertThat(task.getFinishedAt()).isNotNull();
        verify(repository).save(task);
    }

    // ============ UT-MQ-005: token 累加 ============

    @Test
    @DisplayName("UT-MQ-005: 子任务上报 tokenUsed 应累加到 task.tokenUsed")
    void should_AccumulateTokenUsed_When_TokenReported() {
        TaskInstance task = task("tk_005", 0L, 10000L, 100);
        when(repository.findByTaskId("tk_005")).thenReturn(Optional.of(task));
        SubtaskDoneEvent event = doneEvent("ev_005", "tk_005", "success", null, 250, null);

        handler.handle(event);

        assertThat(task.getTokenUsed()).isEqualTo(350);
        verify(repository).save(task);
    }

    // ============ UT-MQ-006: success 分支 ============

    @Test
    @DisplayName("UT-MQ-006: success 状态不应触发任何状态机流转，仅记录日志并持久化")
    void should_NotTransitState_When_StatusIsSuccess() {
        TaskInstance task = task("tk_006", 0L, 10000L, 0);
        when(repository.findByTaskId("tk_006")).thenReturn(Optional.of(task));
        SubtaskDoneEvent event = doneEvent("ev_006", "tk_006", "success", null, null, null);

        handler.handle(event);

        verify(stateMachine, never()).transit(any(), any());
        assertThat(task.getStatus()).isEqualTo("SUBTASK_RUNNING");
        verify(repository).save(task);
    }

    // ============ UT-MQ-007: failed + MAX_RETRY_EXCEEDED → REPLANNING ============

    @Test
    @DisplayName("UT-MQ-007: 子任务失败且错误码为 MAX_RETRY_EXCEEDED 时应流转到 REPLANNING")
    void should_TransitToReplanning_When_MaxRetryExceeded() {
        TaskInstance task = task("tk_007", 0L, 10000L, 0);
        when(repository.findByTaskId("tk_007")).thenReturn(Optional.of(task));
        SubtaskDoneEvent event = doneEvent("ev_007", "tk_007", "failed", null, null, "MAX_RETRY_EXCEEDED");

        handler.handle(event);

        verify(stateMachine).transit(eq(TaskStatus.SUBTASK_RUNNING), eq(TaskStatus.REPLANNING));
        assertThat(task.getStatus()).isEqualTo("REPLANNING");
        verify(repository).save(task);
    }

    // ============ UT-MQ-008: failed + AGENT_NOT_FOUND → WAITING_HUMAN ============

    @Test
    @DisplayName("UT-MQ-008: 子任务失败且错误码为 AGENT_NOT_FOUND 时应流转到 WAITING_HUMAN")
    void should_TransitToWaitingHuman_When_AgentNotFound() {
        TaskInstance task = task("tk_008", 0L, 10000L, 0);
        when(repository.findByTaskId("tk_008")).thenReturn(Optional.of(task));
        SubtaskDoneEvent event = doneEvent("ev_008", "tk_008", "failed", null, null, "AGENT_NOT_FOUND");

        handler.handle(event);

        verify(stateMachine).transit(eq(TaskStatus.SUBTASK_RUNNING), eq(TaskStatus.WAITING_HUMAN));
        assertThat(task.getStatus()).isEqualTo("WAITING_HUMAN");
        verify(repository).save(task);
    }

    // ============ UT-MQ-009: failed + 未知错误码 → WAITING_HUMAN（默认） ============

    @Test
    @DisplayName("UT-MQ-009: 子任务失败且错误码未知时应默认流转到 WAITING_HUMAN")
    void should_TransitToWaitingHuman_When_UnknownErrorCode() {
        TaskInstance task = task("tk_009", 0L, 10000L, 0);
        when(repository.findByTaskId("tk_009")).thenReturn(Optional.of(task));
        SubtaskDoneEvent event = doneEvent("ev_009", "tk_009", "failed", null, null, "UNKNOWN_ERROR");

        handler.handle(event);

        verify(stateMachine).transit(eq(TaskStatus.SUBTASK_RUNNING), eq(TaskStatus.WAITING_HUMAN));
        assertThat(task.getStatus()).isEqualTo("WAITING_HUMAN");
        verify(repository).save(task);
    }

    // ============ UT-MQ-010: require_review → WAITING_HUMAN ============

    @Test
    @DisplayName("UT-MQ-010: 子任务状态为 require_review 时应流转到 WAITING_HUMAN 等待人工审核")
    void should_TransitToWaitingHuman_When_RequireReview() {
        TaskInstance task = task("tk_010", 0L, 10000L, 0);
        when(repository.findByTaskId("tk_010")).thenReturn(Optional.of(task));
        SubtaskDoneEvent event = doneEvent("ev_010", "tk_010", "require_review", null, null, null);

        handler.handle(event);

        verify(stateMachine).transit(eq(TaskStatus.SUBTASK_RUNNING), eq(TaskStatus.WAITING_HUMAN));
        assertThat(task.getStatus()).isEqualTo("WAITING_HUMAN");
        verify(repository).save(task);
    }
}
