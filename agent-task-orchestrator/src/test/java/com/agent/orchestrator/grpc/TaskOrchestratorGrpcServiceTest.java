package com.agent.orchestrator.grpc;

import agentplatform.task.v1.SubmitTaskRequest;
import agentplatform.task.v1.SubmitTaskResponse;
import agentplatform.task.v1.GetTaskStatusRequest;
import agentplatform.task.v1.CancelTaskRequest;
import agentplatform.task.v1.CancelAck;
import agentplatform.task.v1.ReportAck;
import agentplatform.task.v1.SubtaskResult;
import agentplatform.common.v1.TraceContext;
import com.agent.common.constant.TaskStatus;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.TaskInstance;
import com.agent.orchestrator.repository.TaskInstanceRepository;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import com.agent.orchestrator.dispatcher.BatchPartitioner;
import com.agent.orchestrator.validator.PlanValidator;
import com.agent.orchestrator.validator.ValidationContext;
import com.agent.orchestrator.validator.ValidationResult;
import com.agent.orchestrator.template.TemplateMatcher;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TaskOrchestrator gRPC 服务单测。
 *
 * <p>对齐 docs/tests/unit-test-cases.md §5 UT-ORCH-001~012：
 * L1 直跑 / R3 人工 / 重试耗尽重规划 / 非法状态冲突 / 成本超限 / Agent 匹配 / DAG 环检测 / 批次划分。
 * 用 Mockito mock Repository + 下游协作类，专注 gRPC 服务层的请求编排与异常翻译。</p>
 *
 * <p>使用 {@link MockitoSettings} 设为 LENIENT，避免跨用例共享 mock 时严格存根校验误报
 * （部分 mock 仅在某些用例中被 stub，在其他用例中为默认返回值）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskOrchestratorGrpcServiceTest {

    @Mock private TaskInstanceRepository repository;
    @Mock private TaskStateMachine stateMachine;
    @Mock private BatchPartitioner batchPartitioner;
    @Mock private PlanValidator planValidator;
    @Mock private TemplateMatcher templateMatcher;
    @Mock private TaskInstanceMapper mapper;
    @Mock private StreamObserver<SubmitTaskResponse> submitObserver;
    @Mock private StreamObserver<agentplatform.task.v1.TaskInstance> getStatusObserver;
    @Mock private StreamObserver<CancelAck> cancelObserver;
    @Mock private StreamObserver<ReportAck> reportObserver;

    @InjectMocks
    private TaskOrchestratorGrpcService service;

    @BeforeEach
    void setUp() {
        service = new TaskOrchestratorGrpcService(repository, stateMachine, batchPartitioner,
                planValidator, templateMatcher, mapper);
    }

    // ===== UT-ORCH-001: L1 任务直跑（跳规划） =====

    @Test
    @DisplayName("UT-ORCH-001: L1 任务提交后状态应为 RUNNING（跳过 PLANNING）")
    void should_ReturnRunningStatus_When_L1TaskSubmitted() {
        SubmitTaskRequest req = SubmitTaskRequest.newBuilder()
                .setTaskId("tk_l1_001").setTenantId(1001L).setUserId("u_1")
                .setGoal("查订单").setCostLimitCent(1000L)
                .setTrace(TraceContext.newBuilder().setTaskId("tk_l1_001").build())
                .build();
        TaskInstance saved = TaskInstance.builder()
                .taskId("tk_l1_001").tenantId(1001L).complexity(1)
                .status(TaskStatus.RUNNING.name()).costLimitCent(1000L).costUsedCent(0L)
                .tokenUsed(0).build();
        when(repository.findByTaskId("tk_l1_001")).thenReturn(Optional.of(saved));

        service.submitTask(req, submitObserver);

        ArgumentCaptor<SubmitTaskResponse> captor = ArgumentCaptor.forClass(SubmitTaskResponse.class);
        verify(submitObserver).onNext(captor.capture());
        SubmitTaskResponse resp = captor.getValue();
        assertThat(resp.getTaskId()).isEqualTo("tk_l1_001");
        assertThat(resp.getStatus()).isEqualTo(TaskStatus.RUNNING.name());
        assertThat(resp.getComplexity()).isEqualTo(1);
        verify(submitObserver).onCompleted();
    }

    // ===== UT-ORCH-002: R3 节点触发 WAITING_HUMAN =====

    @Test
    @DisplayName("UT-ORCH-002: R3 高危节点应使任务转 WAITING_HUMAN")
    void should_TransitToWaitingHuman_When_R3NodeRequiresReview() {
        // 通过 ReportSubtaskResult 上报 R3 节点要求人工审核
        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_r3_001").setSubtaskId("st_r3").setNodeId("n_r3")
                .setStatus("require_review").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_r3_001").status(TaskStatus.SUBTASK_RUNNING.name()).build();
        when(repository.findByTaskId("tk_r3_001")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.SUBTASK_RUNNING, TaskStatus.WAITING_HUMAN))
                .thenReturn(true);

        service.reportSubtaskResult(result, reportObserver);

        ArgumentCaptor<ReportAck> captor = ArgumentCaptor.forClass(ReportAck.class);
        verify(reportObserver).onNext(captor.capture());
        assertThat(captor.getValue().getAccepted()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.WAITING_HUMAN.name());
    }

    // ===== UT-ORCH-003: 子任务重试耗尽触发 REPLANNING =====

    @Test
    @DisplayName("UT-ORCH-003: 子任务重试耗尽应触发 REPLANNING")
    void should_TransitToReplanning_When_SubtaskRetryExhausted() {
        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_retry_001").setSubtaskId("st_1").setNodeId("n_1")
                .setStatus("failed").setErrorCode("MAX_RETRY_EXCEEDED").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_retry_001").status(TaskStatus.SUBTASK_RUNNING.name()).build();
        when(repository.findByTaskId("tk_retry_001")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.SUBTASK_RUNNING, TaskStatus.REPLANNING))
                .thenReturn(true);

        service.reportSubtaskResult(result, reportObserver);

        verify(reportObserver).onNext(any());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.REPLANNING.name());
    }

    // ===== UT-ORCH-004: 非法状态流转抛 TASK_STATUS_CONFLICT =====

    @Test
    @DisplayName("UT-ORCH-004: 非法状态流转应翻译为 gRPC Status 并调用 onError")
    void should_TranslateStatusConflict_When_IllegalTransition() {
        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_illegal_001").setNodeId("n_1").setStatus("failed").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_illegal_001").status(TaskStatus.SUCCESS.name()).build(); // 终态
        when(repository.findByTaskId("tk_illegal_001")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(eq(TaskStatus.SUCCESS), any()))
                .thenReturn(false);

        service.reportSubtaskResult(result, reportObserver);

        verify(reportObserver).onError(any());
        verify(reportObserver, never()).onNext(any());
    }

    // ===== UT-ORCH-005: 环检测在 DAG 校验阶段抛 DAG_CYCLE_DETECTED =====

    @Test
    @DisplayName("UT-ORCH-005: DAG 含环应使 SubmitTask 返回 DAG_CYCLE_DETECTED 错误")
    void should_ReturnDagCycleError_When_DagHasCircularDependency() {
        // 当复杂度=L2/L3 走 Plan 流程时，DagValidator 抛 DAG_CYCLE_DETECTED
        // gRPC 层应捕获并翻译为 onError
        SubmitTaskRequest req = SubmitTaskRequest.newBuilder()
                .setTaskId("tk_cycle_001").setGoal("复杂任务需规划").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_cycle_001").complexity(2).status(TaskStatus.PLANNING.name()).build();
        when(repository.findByTaskId("tk_cycle_001")).thenReturn(Optional.of(task));
        when(planValidator.validate(any(ValidationContext.class)))
                .thenThrow(new BusinessException(ErrorCode.DAG_CYCLE_DETECTED, "检测到环 A→B→C→A"));

        service.submitTask(req, submitObserver);

        verify(submitObserver).onError(any());
        verify(submitObserver, never()).onNext(any());
    }

    // ===== UT-ORCH-006: 并行批次调度（由 T10 BatchPartitioner 实现，gRPC 层验证调用） =====

    @Test
    @DisplayName("UT-ORCH-006: 提交 L2 任务应调用 BatchPartitioner 划分批次")
    void should_InvokeBatchPartitioner_When_L2TaskSubmitted() {
        SubmitTaskRequest req = SubmitTaskRequest.newBuilder()
                .setTaskId("tk_l2_001").setGoal("多步任务").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_l2_001").complexity(2).status(TaskStatus.PLANNING.name()).build();
        when(repository.findByTaskId("tk_l2_001")).thenReturn(Optional.of(task));
        when(planValidator.validate(any(ValidationContext.class)))
                .thenReturn(ValidationResult.pass());

        service.submitTask(req, submitObserver);

        verify(batchPartitioner, atLeastOnce()).partition(any());
    }

    // ===== UT-ORCH-007: Agent 匹配成功推进到 SUBTASK_RUNNING =====

    @Test
    @DisplayName("UT-ORCH-007: Agent 匹配成功应推进任务到 SUBTASK_RUNNING")
    void should_AdvanceToSubtaskRunning_When_AgentMatched() {
        SubmitTaskRequest req = SubmitTaskRequest.newBuilder()
                .setTaskId("tk_agent_ok").setGoal("查询订单").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_agent_ok").complexity(1).status(TaskStatus.RUNNING.name()).build();
        when(repository.findByTaskId("tk_agent_ok")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.RUNNING, TaskStatus.SUBTASK_RUNNING)).thenReturn(true);

        service.submitTask(req, submitObserver);

        verify(submitObserver).onNext(any());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUBTASK_RUNNING.name());
    }

    // ===== UT-ORCH-008: 所有 Agent 评分 <0.6 抛 AGENT_NOT_FOUND 转 WAITING_HUMAN =====

    @Test
    @DisplayName("UT-ORCH-008: Agent 匹配失败应使任务转 WAITING_HUMAN")
    void should_TransitToWaitingHuman_When_NoAgentScoreAbove06() {
        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_no_agent").setNodeId("n_1").setStatus("failed")
                .setErrorCode("AGENT_NOT_FOUND").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_no_agent").status(TaskStatus.SUBTASK_RUNNING.name()).build();
        when(repository.findByTaskId("tk_no_agent")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.SUBTASK_RUNNING, TaskStatus.WAITING_HUMAN))
                .thenReturn(true);

        service.reportSubtaskResult(result, reportObserver);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.WAITING_HUMAN.name());
    }

    // ===== UT-ORCH-012: 成本超限抛 COST_BUDGET_EXCEEDED =====

    @Test
    @DisplayName("UT-ORCH-012: 成本超限应抛 COST_BUDGET_EXCEEDED 并转 TIMEOUT")
    void should_ThrowCostBudgetExceeded_When_CostUsedExceedsLimit() {
        SubtaskResult result = SubtaskResult.newBuilder()
                .setTaskId("tk_cost_over").setNodeId("n_1").setStatus("success")
                .setCostCent(5500L).build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_cost_over").status(TaskStatus.SUBTASK_RUNNING.name())
                .costLimitCent(5000L).costUsedCent(0L).build();
        when(repository.findByTaskId("tk_cost_over")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.SUBTASK_RUNNING, TaskStatus.TIMEOUT)).thenReturn(true);

        service.reportSubtaskResult(result, reportObserver);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.TIMEOUT.name());
    }

    // ===== GetTaskStatus 正常路径 =====

    @Test
    @DisplayName("GetTaskStatus: 任务存在应返回 TaskInstance proto")
    void should_ReturnTaskInstance_When_TaskExists() {
        GetTaskStatusRequest req = GetTaskStatusRequest.newBuilder()
                .setTaskId("tk_q_001").setTenantId(1001L).build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_q_001").tenantId(1001L).status(TaskStatus.RUNNING.name())
                .complexity(2).build();
        when(repository.findByTaskId("tk_q_001")).thenReturn(Optional.of(task));
        agentplatform.task.v1.TaskInstance proto = agentplatform.task.v1.TaskInstance.newBuilder()
                .setTaskId("tk_q_001").setStatus(TaskStatus.RUNNING.name()).build();
        when(mapper.toProto(task)).thenReturn(proto);

        service.getTaskStatus(req, getStatusObserver);

        verify(getStatusObserver).onNext(proto);
        verify(getStatusObserver).onCompleted();
    }

    // ===== GetTaskStatus 任务不存在 =====

    @Test
    @DisplayName("GetTaskStatus: 任务不存在应翻译为 TASK_NOT_FOUND 错误")
    void should_ReturnTaskNotFound_When_TaskIdMissing() {
        GetTaskStatusRequest req = GetTaskStatusRequest.newBuilder()
                .setTaskId("tk_notexist").build();
        when(repository.findByTaskId("tk_notexist")).thenReturn(Optional.empty());

        service.getTaskStatus(req, getStatusObserver);

        verify(getStatusObserver).onError(any());
    }

    // ===== CancelTask 正常路径 =====

    @Test
    @DisplayName("CancelTask: 取消运行中任务应转 CANCELLED")
    void should_TransitToCancelled_When_CancelRunningTask() {
        CancelTaskRequest req = CancelTaskRequest.newBuilder()
                .setTaskId("tk_cancel_001").setReason("user cancel").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_cancel_001").status(TaskStatus.RUNNING.name()).build();
        when(repository.findByTaskId("tk_cancel_001")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.RUNNING, TaskStatus.CANCELLED)).thenReturn(true);

        service.cancelTask(req, cancelObserver);

        ArgumentCaptor<CancelAck> captor = ArgumentCaptor.forClass(CancelAck.class);
        verify(cancelObserver).onNext(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.CANCELLED.name());
    }

    // ===== CancelTask 非法状态冲突 =====

    @Test
    @DisplayName("CancelTask: 取消终态任务应抛 TASK_STATUS_CONFLICT 并调用 onError")
    void should_ReturnStatusConflict_When_CancelTerminalTask() {
        CancelTaskRequest req = CancelTaskRequest.newBuilder()
                .setTaskId("tk_cancel_term").setReason("user cancel").build();
        TaskInstance task = TaskInstance.builder()
                .taskId("tk_cancel_term").status(TaskStatus.SUCCESS.name()).build();
        when(repository.findByTaskId("tk_cancel_term")).thenReturn(Optional.of(task));
        when(stateMachine.canTransitTo(TaskStatus.SUCCESS, TaskStatus.CANCELLED)).thenReturn(false);

        service.cancelTask(req, cancelObserver);

        verify(cancelObserver).onError(any());
        verify(cancelObserver, never()).onNext(any());
    }
}
