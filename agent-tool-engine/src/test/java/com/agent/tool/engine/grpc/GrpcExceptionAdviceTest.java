package com.agent.tool.engine.grpc;

import com.agent.tool.engine.exception.ToolApprovalException;
import com.agent.tool.engine.exception.ToolDisabledException;
import com.agent.tool.engine.exception.ToolEngineException;
import com.agent.tool.engine.exception.ToolExecutionTimeoutException;
import com.agent.tool.engine.exception.ToolNotFoundException;
import com.agent.tool.engine.exception.ToolQuotaExhaustedException;
import com.agent.tool.engine.exception.ToolSandboxFailureException;
import com.agent.tool.engine.exception.ToolValidationException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * T12 {@link GrpcExceptionAdvice} unit tests.
 *
 * <p>Verifies each domain exception subclass maps to the correct gRPC
 * {@link Status.Code}, and the advice propagates the error via
 * {@code observer.onError}.</p>
 */
class GrpcExceptionAdviceTest {

    private final GrpcExceptionAdvice advice = new GrpcExceptionAdvice();

    @Test
    @DisplayName("toolNotFoundException -> NOT_FOUND")
    void toolNotFoundException_returnsNotFound() {
        Status status = advice.toStatus(
                new ToolNotFoundException("工具未注册: tool-x"));
        assertThat(status.getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(status.getDescription()).contains("TOOL_NOT_FOUND");
        assertThat(status.getDescription()).contains("tool-x");
    }

    @Test
    @DisplayName("toolDisabledException -> PERMISSION_DENIED")
    void toolDisabledException_returnsPermissionDenied() {
        Status status = advice.toStatus(
                new ToolDisabledException("工具已禁用"));
        assertThat(status.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(status.getDescription()).contains("TOOL_DISABLED");
    }

    @Test
    @DisplayName("toolApprovalException -> PERMISSION_DENIED")
    void toolApprovalException_returnsPermissionDenied() {
        Status status = advice.toStatus(
                new ToolApprovalException(ToolApprovalException.CODE_APPROVAL_REQUIRED,
                        "缺少审批"));
        assertThat(status.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(status.getDescription()).contains("APPROVAL_REQUIRED");
    }

    @Test
    @DisplayName("toolValidationException -> INVALID_ARGUMENT")
    void toolValidationException_returnsInvalidArgument() {
        Status status = advice.toStatus(
                new ToolValidationException("toolId 不能为空"));
        assertThat(status.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(status.getDescription()).contains("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("toolQuotaExhaustedException -> RESOURCE_EXHAUSTED")
    void toolQuotaExhaustedException_returnsResourceExhausted() {
        Status status = advice.toStatus(
                new ToolQuotaExhaustedException("限流触发"));
        assertThat(status.getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(status.getDescription()).contains("RATE_LIMITED");
    }

    @Test
    @DisplayName("toolExecutionTimeoutException -> DEADLINE_EXCEEDED")
    void toolExecutionTimeoutException_returnsDeadlineExceeded() {
        Status status = advice.toStatus(
                new ToolExecutionTimeoutException("执行超时 30000ms"));
        assertThat(status.getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
        assertThat(status.getDescription()).contains("TOOL_EXECUTION_TIMEOUT");
    }

    @Test
    @DisplayName("toolSandboxFailureException -> INTERNAL")
    void toolSandboxFailureException_returnsInternal() {
        Status status = advice.toStatus(
                new ToolSandboxFailureException("容器启动失败"));
        assertThat(status.getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(status.getDescription()).contains("TOOL_SANDBOX_FAILURE");
    }

    @Test
    @DisplayName("toolEngineException base -> INTERNAL with errorCode")
    void toolEngineException_returnsInternalWithCode() {
        Status status = advice.toStatus(
                new ToolEngineException("CUSTOM_ERROR", 500, "未知错误"));
        assertThat(status.getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(status.getDescription()).contains("CUSTOM_ERROR");
    }

    @Test
    @DisplayName("illegalArgumentException -> INVALID_ARGUMENT")
    void illegalArgumentException_returnsInvalidArgument() {
        Status status = advice.toStatus(new IllegalArgumentException("bad arg"));
        assertThat(status.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(status.getDescription()).contains("bad arg");
    }

    @Test
    @DisplayName("genericException -> INTERNAL")
    void genericException_returnsInternal() {
        Status status = advice.toStatus(new RuntimeException("oops"));
        assertThat(status.getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(status.getDescription()).contains("oops");
    }

    @Test
    @DisplayName("translate calls observer.onError with StatusRuntimeException")
    void translate_callsObserverOnError() {
        @SuppressWarnings("unchecked")
        StreamObserver<Object> observer = mock(StreamObserver.class);

        advice.translate(new ToolNotFoundException("not found"), observer);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }
}
