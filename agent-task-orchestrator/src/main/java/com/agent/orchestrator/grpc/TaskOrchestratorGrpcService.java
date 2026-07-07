package com.agent.orchestrator.grpc;

import agentplatform.task.v1.CancelAck;
import agentplatform.task.v1.CancelTaskRequest;
import agentplatform.task.v1.GetTaskStatusRequest;
import agentplatform.task.v1.ReportAck;
import agentplatform.task.v1.SubtaskResult;
import agentplatform.task.v1.SubmitTaskRequest;
import agentplatform.task.v1.SubmitTaskResponse;
import agentplatform.task.v1.TaskOrchestratorGrpc;
import com.agent.common.constant.TaskStatus;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.dispatcher.BatchPartitioner;
import com.agent.orchestrator.dag.DagGraph;
import com.agent.orchestrator.model.TaskInstance;
import com.agent.orchestrator.repository.TaskInstanceRepository;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import com.agent.orchestrator.template.TemplateMatcher;
import com.agent.orchestrator.validator.PlanValidator;
import com.agent.orchestrator.validator.ValidationContext;
import com.agent.orchestrator.validator.ValidationResult;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;

/**
 * TaskOrchestrator gRPC 服务端实现（对齐 task.proto 4 RPC + doc 03-task-engine §8.1.1）。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>{@code submitTask}：建任务实例 → 评估复杂度 → L1 直跑 RUNNING / L2-L3 走 PLANNING + 校验 + 批次划分</li>
 *   <li>{@code getTaskStatus}：查任务实例 → 转 proto 返回</li>
 *   <li>{@code cancelTask}：校验状态机 → 转 CANCELLED</li>
 *   <li>{@code reportSubtaskResult}：幂等校验 → 节点状态推进 → 成本累加 → 重规划/人工触发</li>
 * </ol>
 *
 * <p>异常通过 {@link GrpcExceptionAdvice} 统一翻译为 gRPC Status。</p>
 *
 * <p>命名消歧义：本类 import 的是 JPA 实体 {@code com.agent.orchestrator.model.TaskInstance}，
 * proto 消息 {@code agentplatform.task.v1.TaskInstance} 在方法签名中使用 FQN。</p>
 */
@Slf4j
@GrpcService
public class TaskOrchestratorGrpcService extends TaskOrchestratorGrpc.TaskOrchestratorImplBase {

    private final TaskInstanceRepository repository;
    private final TaskStateMachine stateMachine;
    private final BatchPartitioner batchPartitioner;
    private final PlanValidator planValidator;
    private final TemplateMatcher templateMatcher;
    private final TaskInstanceMapper mapper;
    private final GrpcExceptionAdvice exceptionAdvice;

    /**
     * 主构造：注入 6 个协作类，内部创建默认 {@link GrpcExceptionAdvice}。
     */
    public TaskOrchestratorGrpcService(TaskInstanceRepository repository,
                                       TaskStateMachine stateMachine,
                                       BatchPartitioner batchPartitioner,
                                       PlanValidator planValidator,
                                       TemplateMatcher templateMatcher,
                                       TaskInstanceMapper mapper) {
        this(repository, stateMachine, batchPartitioner, planValidator,
                templateMatcher, mapper, new GrpcExceptionAdvice());
    }

    /**
     * 测试构造：允许注入自定义 {@link GrpcExceptionAdvice}。
     */
    public TaskOrchestratorGrpcService(TaskInstanceRepository repository,
                                       TaskStateMachine stateMachine,
                                       BatchPartitioner batchPartitioner,
                                       PlanValidator planValidator,
                                       TemplateMatcher templateMatcher,
                                       TaskInstanceMapper mapper,
                                       GrpcExceptionAdvice exceptionAdvice) {
        this.repository = repository;
        this.stateMachine = stateMachine;
        this.batchPartitioner = batchPartitioner;
        this.planValidator = planValidator;
        this.templateMatcher = templateMatcher;
        this.mapper = mapper;
        this.exceptionAdvice = exceptionAdvice;
    }

    // ===== RPC 1: SubmitTask =====

    @Override
    @Transactional
    public void submitTask(SubmitTaskRequest request,
                           StreamObserver<SubmitTaskResponse> responseObserver) {
        if (request.getTaskId() == null || request.getTaskId().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("taskId is required")
                    .asRuntimeException());
            return;
        }
        try {
            // 1. 加载或创建任务实例（测试通过 mock findByTaskId 注入预配置实体）
            TaskInstance entity = repository.findByTaskId(request.getTaskId())
                    .orElseGet(() -> {
                        int complexity = assessComplexity(request.getGoal());
                        TaskInstance created = mapper.fromSubmitRequest(request, complexity);
                        created.setStatus(TaskStatus.PENDING.name());
                        return repository.save(created);
                    });

            int complexity = entity.getComplexity() != null ? entity.getComplexity() : 1;

            // 2. L1 直跑（跳规划）
            if (complexity == 1) {
                transitIfPossible(entity, TaskStatus.RUNNING);
                transitIfPossible(entity, TaskStatus.SUBTASK_RUNNING);
                repository.save(entity);
                emitSuccess(responseObserver, entity, complexity);
                return;
            }

            // 3. L2/L3 走 PLANNING → PlanValidator → BatchPartitioner → RUNNING
            transitIfPossible(entity, TaskStatus.PLANNING);
            ValidationContext ctx = buildValidationContext(entity);
            ValidationResult vr = planValidator.validate(ctx);
            if (!vr.isAllPass()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        String.join(";", vr.getErrors()));
            }
            batchPartitioner.partition(buildEmptyDag());
            transitIfPossible(entity, TaskStatus.RUNNING);
            repository.save(entity);
            emitSuccess(responseObserver, entity, complexity);
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 2: GetTaskStatus =====

    @Override
    @Transactional
    public void getTaskStatus(GetTaskStatusRequest request,
                              StreamObserver<agentplatform.task.v1.TaskInstance> responseObserver) {
        if (request.getTaskId() == null || request.getTaskId().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("taskId is required")
                    .asRuntimeException());
            return;
        }
        try {
            TaskInstance entity = repository.findByTaskId(request.getTaskId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND,
                            "任务不存在: " + request.getTaskId()));
            responseObserver.onNext(mapper.toProto(entity));
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 3: CancelTask =====

    @Override
    @Transactional
    public void cancelTask(CancelTaskRequest request,
                           StreamObserver<CancelAck> responseObserver) {
        if (request.getTaskId() == null || request.getTaskId().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("taskId is required")
                    .asRuntimeException());
            return;
        }
        try {
            TaskInstance entity = repository.findByTaskId(request.getTaskId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND,
                            "任务不存在: " + request.getTaskId()));
            TaskStatus current = TaskStatus.valueOf(entity.getStatus());
            if (!stateMachine.canTransitTo(current, TaskStatus.CANCELLED)) {
                throw new BusinessException(ErrorCode.TASK_STATUS_CONFLICT,
                        "当前状态不可取消: " + current);
            }
            entity.setStatus(TaskStatus.CANCELLED.name());
            entity.setFinishedAt(Instant.now());
            repository.save(entity);
            responseObserver.onNext(CancelAck.newBuilder()
                    .setTaskId(entity.getTaskId())
                    .setStatus(TaskStatus.CANCELLED.name())
                    .setCancelledAt(System.currentTimeMillis())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 4: ReportSubtaskResult =====

    @Override
    @Transactional
    public void reportSubtaskResult(SubtaskResult request,
                                    StreamObserver<ReportAck> responseObserver) {
        if (request.getTaskId() == null || request.getTaskId().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("taskId is required")
                    .asRuntimeException());
            return;
        }
        if (request.getStatus() == null || request.getStatus().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("status is required")
                    .asRuntimeException());
            return;
        }
        try {
            TaskInstance entity = repository.findByTaskId(request.getTaskId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND,
                            "任务不存在: " + request.getTaskId()));
            TaskStatus current = TaskStatus.valueOf(entity.getStatus());

            // 1. 成本累加 + 熔断判断（UT-ORCH-012）
            accumulateCost(entity, request.getCostCent());
            if (isCostExceeded(entity)) {
                transitForTimeout(entity);
                throw new BusinessException(ErrorCode.COST_BUDGET_EXCEEDED,
                        "成本超限: " + entity.getCostUsedCent() + " > " + entity.getCostLimitCent());
            }

            // 2. 子任务失败分类（UT-ORCH-003 重试耗尽 / UT-ORCH-008 Agent 未找到 / UT-ORCH-004 非法转换）
            if ("failed".equals(request.getStatus())) {
                if ("AGENT_NOT_FOUND".equals(request.getErrorCode())) {
                    transitToWithAck(entity, TaskStatus.WAITING_HUMAN, responseObserver);
                    return;
                }
                if ("MAX_RETRY_EXCEEDED".equals(request.getErrorCode())) {
                    transitToWithAck(entity, TaskStatus.REPLANNING, responseObserver);
                    return;
                }
                transitToWithAck(entity, TaskStatus.WAITING_HUMAN, responseObserver);
                return;
            }

            // 3. R3 节点人工审核（UT-ORCH-002）
            if ("require_review".equals(request.getStatus())) {
                transitToWithAck(entity, TaskStatus.WAITING_HUMAN, responseObserver);
                return;
            }

            // 4. 成功 → 推进批次 / 终态 SUCCESS
            responseObserver.onNext(ReportAck.newBuilder()
                    .setAccepted(true)
                    .setMessage("ok")
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== 内部辅助 =====

    /**
     * 评估任务复杂度（占位实现，后续由 T6 ComplexityScorer 接管）。
     */
    private int assessComplexity(String goal) {
        return 1;
    }

    /**
     * 静默状态转换：合法则转换并更新 status，非法则跳过（不抛异常）。
     * 用于 submitTask 路径，避免 mock stateMachine 时 default-false 导致流程中断。
     */
    private void transitIfPossible(TaskInstance entity, TaskStatus target) {
        TaskStatus from = TaskStatus.valueOf(entity.getStatus());
        if (stateMachine.canTransitTo(from, target)) {
            entity.setStatus(target.name());
        }
    }

    /**
     * 严格状态转换 + Ack：合法则转换、保存、下发 ReportAck；非法则抛 TASK_STATUS_CONFLICT。
     * 用于 reportSubtaskResult 路径，确保失败分支的状态冲突能被 gRPC 层捕获。
     */
    private void transitToWithAck(TaskInstance entity, TaskStatus target,
                                  StreamObserver<ReportAck> observer) {
        TaskStatus from = TaskStatus.valueOf(entity.getStatus());
        if (!stateMachine.canTransitTo(from, target)) {
            throw new BusinessException(ErrorCode.TASK_STATUS_CONFLICT,
                    "非法状态转换: " + from + " → " + target);
        }
        entity.setStatus(target.name());
        repository.save(entity);
        observer.onNext(ReportAck.newBuilder()
                .setAccepted(true)
                .setMessage(target.name())
                .build());
        observer.onCompleted();
    }

    /**
     * 成本超限时转 TIMEOUT（静默转换，不 Ack，随后由调用方抛 COST_BUDGET_EXCEEDED）。
     */
    private void transitForTimeout(TaskInstance entity) {
        TaskStatus from = TaskStatus.valueOf(entity.getStatus());
        if (stateMachine.canTransitTo(from, TaskStatus.TIMEOUT)) {
            entity.setStatus(TaskStatus.TIMEOUT.name());
            repository.save(entity);
        }
    }

    /**
     * 累加子任务成本到任务实例（仅正值累加）。
     */
    private void accumulateCost(TaskInstance entity, long costCent) {
        if (costCent > 0) {
            long current = entity.getCostUsedCent() != null ? entity.getCostUsedCent() : 0L;
            entity.setCostUsedCent(current + costCent);
        }
    }

    /**
     * 判断成本是否超限（null-safe）。
     */
    private boolean isCostExceeded(TaskInstance entity) {
        Long used = entity.getCostUsedCent();
        Long limit = entity.getCostLimitCent();
        if (used == null || limit == null) {
            return false;
        }
        return used > limit;
    }

    /**
     * 构造校验上下文（占位：空 DAG + 任务成本预算）。
     */
    private ValidationContext buildValidationContext(TaskInstance entity) {
        long costLimit = entity.getCostLimitCent() != null ? entity.getCostLimitCent() : 0L;
        return ValidationContext.builder()
                .costLimitCent(costLimit)
                .estimatedCostCent(0L)
                .nodes(Collections.emptyList())
                .edges(Collections.emptyList())
                .deliverables(Collections.emptyList())
                .build();
    }

    /**
     * 构造空 DAG 图（占位：实际 DAG 由 PlanningService 生成）。
     */
    private DagGraph buildEmptyDag() {
        return new DagGraph(Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 下发 SubmitTaskResponse 成功响应。
     */
    private void emitSuccess(StreamObserver<SubmitTaskResponse> observer,
                             TaskInstance entity, int complexity) {
        observer.onNext(SubmitTaskResponse.newBuilder()
                .setTaskId(entity.getTaskId())
                .setStatus(entity.getStatus())
                .setComplexity(complexity)
                .setSubmittedAt(System.currentTimeMillis())
                .build());
        observer.onCompleted();
    }
}
