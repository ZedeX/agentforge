package com.agent.orchestrator.e2e;

import agentplatform.task.v1.GetTaskStatusRequest;
import agentplatform.task.v1.TaskOrchestratorGrpc;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.fixture.ErrorCodeGrpcFixtureService;
import com.agent.orchestrator.grpc.GrpcExceptionAdvice;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * P7-5 (COV-04)：错误码 gRPC 端到端触发路径测试。
 *
 * <p>本测试配合 P6-7 的 {@code ErrorCodePathTest}（单元层）补足「真实 gRPC 链路」覆盖：
 * <ol>
 *   <li>客户端通过 {@link TaskOrchestratorGrpc.TaskOrchestratorBlockingStub} 调用 {@code getTaskStatus}</li>
 *   <li>{@link ErrorCodeGrpcFixtureService} 根据 {@code taskId} 抛出对应 {@link
 *       com.agent.common.exception.BusinessException}</li>
 *   <li>真实 {@link GrpcExceptionAdvice} 翻译为 {@link Status} 并通过 {@code observer.onError} 下发</li>
 *   <li>客户端收到 {@link StatusRuntimeException}，用 AssertJ 断言 {@link Status.Code} 与 description</li>
 * </ol>
 *
 * <p>覆盖 {@link GrpcExceptionAdvice} 的全部 switch 分支：</p>
 * <ul>
 *   <li>404 → {@link Status.Code#NOT_FOUND}</li>
 *   <li>409 → {@link Status.Code#FAILED_PRECONDITION}</li>
 *   <li>429 → {@link Status.Code#RESOURCE_EXHAUSTED}</li>
 *   <li>400 → {@link Status.Code#INVALID_ARGUMENT}</li>
 *   <li>default → {@link Status.Code#INTERNAL}</li>
 *   <li>非 BusinessException → {@link Status.Code#INTERNAL} with "INTERNAL: " 前缀</li>
 * </ul>
 *
 * <p>基础设施：使用 grpc-testing 的 {@link InProcessServerBuilder} + {@link InProcessChannelBuilder}，
 * directExecutor，无真实端口、无 Spring 上下文、无 Docker，启动开销极小。</p>
 *
 * <p>断言风格：AssertJ 链式 + {@link catchThrowableOfType} 捕获异常，无 JUnit assertThrows，符合 P6-4 规范。</p>
 */
class ErrorCodeGrpcE2ETest {

    private static Server grpcServer;
    private static ManagedChannel channel;
    private static TaskOrchestratorGrpc.TaskOrchestratorBlockingStub stub;

    @BeforeAll
    static void setUp() throws Exception {
        // 真实 GrpcExceptionAdvice + fixture service：每个 RPC 抛 BusinessException 后由 advice 翻译
        ErrorCodeGrpcFixtureService service =
                new ErrorCodeGrpcFixtureService(new GrpcExceptionAdvice());

        String serverName = InProcessServerBuilder.generateName();
        grpcServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        stub = TaskOrchestratorGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
    }

    /** 构造 getTaskStatus 请求，taskId 即 ErrorCode 枚举名。 */
    private static GetTaskStatusRequest req(String taskId) {
        return GetTaskStatusRequest.newBuilder().setTaskId(taskId).build();
    }

    /** 调用 stub.getTaskStatus 并捕获 StatusRuntimeException；用 AssertJ catchThrowableOfType 替代 assertThrows。 */
    private static StatusRuntimeException callAndCapture(String taskId) {
        return catchThrowableOfType(
                () -> stub.getTaskStatus(req(taskId)),
                StatusRuntimeException.class);
    }

    // ============ 404 → NOT_FOUND ============

    @Test
    @DisplayName("gRPC E2E: TASK_NOT_FOUND(404) 应翻译为 Status NOT_FOUND")
    void should_ReturnNotFound_When_TaskNotFound() {
        StatusRuntimeException ex = callAndCapture(ErrorCode.TASK_NOT_FOUND.name());

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(ex.getStatus().getDescription())
                .contains(ErrorCode.TASK_NOT_FOUND.getCode())
                .contains("e2e:" + ErrorCode.TASK_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("gRPC E2E: AGENT_NOT_FOUND(404) 应翻译为 Status NOT_FOUND")
    void should_ReturnNotFound_When_AgentNotFound() {
        StatusRuntimeException ex = callAndCapture(ErrorCode.AGENT_NOT_FOUND.name());

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(ex.getStatus().getDescription()).startsWith(ErrorCode.AGENT_NOT_FOUND.getCode() + ":");
    }

    // ============ 409 → FAILED_PRECONDITION ============

    @Test
    @DisplayName("gRPC E2E: TASK_STATUS_CONFLICT(409) 应翻译为 Status FAILED_PRECONDITION")
    void should_ReturnFailedPrecondition_When_TaskStatusConflict() {
        StatusRuntimeException ex = callAndCapture(ErrorCode.TASK_STATUS_CONFLICT.name());

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(ex.getStatus().getDescription()).contains(ErrorCode.TASK_STATUS_CONFLICT.getCode());
    }

    @Test
    @DisplayName("gRPC E2E: DAG_CYCLE_DETECTED(409) 应翻译为 Status FAILED_PRECONDITION")
    void should_ReturnFailedPrecondition_When_DagCycleDetected() {
        StatusRuntimeException ex = callAndCapture(ErrorCode.DAG_CYCLE_DETECTED.name());

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(ex.getStatus().getDescription()).contains(ErrorCode.DAG_CYCLE_DETECTED.getCode());
    }

    // ============ 429 → RESOURCE_EXHAUSTED ============

    @Test
    @DisplayName("gRPC E2E: COST_BUDGET_EXCEEDED(429) 应翻译为 Status RESOURCE_EXHAUSTED")
    void should_ReturnResourceExhausted_When_CostBudgetExceeded() {
        StatusRuntimeException ex = callAndCapture(ErrorCode.COST_BUDGET_EXCEEDED.name());

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(ex.getStatus().getDescription()).contains(ErrorCode.COST_BUDGET_EXCEEDED.getCode());
    }

    // ============ 400 → INVALID_ARGUMENT ============

    @Test
    @DisplayName("gRPC E2E: PARAM_INVALID(400) 应翻译为 Status INVALID_ARGUMENT")
    void should_ReturnInvalidArgument_When_ParamInvalid() {
        StatusRuntimeException ex = callAndCapture(ErrorCode.PARAM_INVALID.name());

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(ex.getStatus().getDescription()).contains(ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("gRPC E2E: VALIDATION_FAILED(400) 应翻译为 Status INVALID_ARGUMENT")
    void should_ReturnInvalidArgument_When_ValidationFailed() {
        StatusRuntimeException ex = callAndCapture(ErrorCode.VALIDATION_FAILED.name());

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(ex.getStatus().getDescription()).contains(ErrorCode.VALIDATION_FAILED.getCode());
    }

    // ============ default → INTERNAL（500 内部错误） ============

    @Test
    @DisplayName("gRPC E2E: INTERNAL(500) 应落入 default 分支翻译为 Status INTERNAL")
    void should_ReturnInternal_When_DefaultBranchInternal() {
        StatusRuntimeException ex = callAndCapture(ErrorCode.INTERNAL.name());

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(ex.getStatus().getDescription()).contains(ErrorCode.INTERNAL.getCode());
    }

    @Test
    @DisplayName("gRPC E2E: REPLAN_EXHAUSTED(500) 应落入 default 分支翻译为 Status INTERNAL")
    void should_ReturnInternal_When_ReplanExhausted() {
        StatusRuntimeException ex = callAndCapture(ErrorCode.REPLAN_EXHAUSTED.name());

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(ex.getStatus().getDescription()).contains(ErrorCode.REPLAN_EXHAUSTED.getCode());
    }

    // ============ 兜底分支：非 BusinessException → INTERNAL with "INTERNAL: " 前缀 ============

    @Test
    @DisplayName("gRPC E2E: 非 BusinessException 应由兜底分支翻译为 INTERNAL with 'INTERNAL: ' 前缀")
    void should_ReturnInternalWithInternalPrefix_When_NonBusinessException() {
        StatusRuntimeException ex = callAndCapture("_runtime");

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(ex.getStatus().getDescription())
                .startsWith("INTERNAL:")
                .contains("runtime failure for e2e");
    }

    // ============ 同步路径：通过自定义 StreamObserver 验证 advice.translate 下发 ============

    @Test
    @DisplayName("gRPC E2E: advice.translate 应在 StreamObserver 上调用 onError 下发 StatusRuntimeException")
    void should_InvokeObserverOnError_When_AdviceTranslates() {
        // 该用例直接验证 GrpcExceptionAdvice.translate 行为：构造一个收集型 StreamObserver，
        // 调用 advice.translate 后断言 onError 被调用且 Throwable 类型为 StatusRuntimeException
        AtomicReference<Throwable> captured = new AtomicReference<>();
        StreamObserver<agentplatform.task.v1.TaskInstance> collectingObserver = new StreamObserver<>() {
            @Override
            public void onNext(agentplatform.task.v1.TaskInstance value) {
                throw new AssertionError("不应调用 onNext");
            }

            @Override
            public void onError(Throwable t) {
                captured.set(t);
            }

            @Override
            public void onCompleted() {
                throw new AssertionError("不应调用 onCompleted");
            }
        };

        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();
        advice.translate(
                new com.agent.common.exception.BusinessException(ErrorCode.TASK_NOT_FOUND, "e2e"),
                collectingObserver);

        assertThat(captured.get())
                .isNotNull()
                .isInstanceOfSatisfying(StatusRuntimeException.class, ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                    assertThat(ex.getStatus().getDescription()).contains(ErrorCode.TASK_NOT_FOUND.getCode());
                });
    }
}
