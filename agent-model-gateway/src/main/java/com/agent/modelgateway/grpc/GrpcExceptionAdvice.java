package com.agent.modelgateway.grpc;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC 异常翻译器（Plan 07 T8，复用 agent-task-orchestrator / agent-knowledge 模式）。
 *
 * <p>将 {@link BusinessException} 翻译为 gRPC {@link Status} 并通过
 * {@code observer.onError} 下发，避免服务方法抛未捕获异常导致 channel 异常断开。</p>
 *
 * <p>错误码 → gRPC Status 映射（对齐 Plan 07 UT-MG-008 / UT-MG-006）：</p>
 * <ul>
 *   <li>MODEL_GATEWAY_ERROR(500) → UNKNOWN（上游模型错误，对齐 UT-MG-008）</li>
 *   <li>MODEL_TIMEOUT(504) / TIMEOUT(504) / TOOL_TIMEOUT(504) → DEADLINE_EXCEEDED</li>
 *   <li>QUOTA_EXCEEDED(429) / RATE_LIMITED(429) / COST_BUDGET_EXCEEDED(429) → RESOURCE_EXHAUSTED（UT-MG-006）</li>
 *   <li>PARAM_INVALID(400) / VALIDATION_FAILED(400) / CONTENT_BLOCKED(400) → INVALID_ARGUMENT</li>
 *   <li>MODEL_NOT_FOUND(404) / *_NOT_FOUND(404) → NOT_FOUND</li>
 *   <li>409 状态冲突 → FAILED_PRECONDITION</li>
 *   <li>其他 500 内部错误 → INTERNAL</li>
 *   <li>非 BusinessException → INTERNAL with "internal error"</li>
 * </ul>
 *
 * <p>设计说明：采用 @Component + 手动调用 {@link #translate} 的方式，而非 net.devh 的
 * {@code @GrpcAdvice} 注解模式，以显式控制 onError 调用时机。</p>
 */
@Component
public class GrpcExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionAdvice.class);

    /**
     * 将 Throwable 翻译为 gRPC Status 并通过 observer.onError 下发。
     * S-12: All gRPC exceptions are logged with status code + description + full stack trace.
     *
     * @param t        业务异常或其他异常
     * @param observer gRPC 响应观察者
     * @param <T>      响应类型
     */
    public <T> void translate(Throwable t, StreamObserver<T> observer) {
        Status status = toStatus(t);
        if (log.isWarnEnabled()) {
            log.warn("gRPC exception -> status={} desc={}", status.getCode(),
                    status.getDescription(), t);
        }
        observer.onError(status.asRuntimeException());
    }

    /**
     * 将 Throwable 翻译为 gRPC Status（含 Description）。
     */
    private Status toStatus(Throwable t) {
        if (t instanceof BusinessException be) {
            ErrorCode ec = be.getErrorCode();
            // Plan 07 UT-MG-008: ProviderUnavailable → MODEL_GATEWAY_ERROR → gRPC UNKNOWN
            if (ec == ErrorCode.MODEL_GATEWAY_ERROR) {
                return Status.UNKNOWN.withDescription(ec.getCode() + ": " + be.getMessage());
            }
            return switch (ec.getHttpStatus()) {
                case 404 -> Status.NOT_FOUND.withDescription(ec.getCode() + ": " + be.getMessage());
                case 409 -> Status.FAILED_PRECONDITION.withDescription(ec.getCode() + ": " + be.getMessage());
                case 429 -> Status.RESOURCE_EXHAUSTED.withDescription(ec.getCode() + ": " + be.getMessage());
                case 504 -> Status.DEADLINE_EXCEEDED.withDescription(ec.getCode() + ": " + be.getMessage());
                case 400 -> Status.INVALID_ARGUMENT.withDescription(ec.getCode() + ": " + be.getMessage());
                default -> Status.INTERNAL.withDescription(ec.getCode() + ": " + be.getMessage());
            };
        }
        return Status.INTERNAL.withDescription("INTERNAL: " + t.getMessage());
    }
}
