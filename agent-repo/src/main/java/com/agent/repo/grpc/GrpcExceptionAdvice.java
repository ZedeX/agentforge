package com.agent.repo.grpc;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC 异常翻译器（Plan 08 T4，复用 agent-knowledge / agent-model-gateway 模式）。
 *
 * <p>将 {@link BusinessException} 翻译为 gRPC {@link Status} 并通过
 * {@code observer.onError} 下发，避免服务方法抛未捕获异常导致 channel 异常断开。</p>
 *
 * <p>错误码 → gRPC Status 映射（对齐 Plan 08 T4 Red-2）：</p>
 * <ul>
 *   <li>AGENT_NOT_FOUND(404) → NOT_FOUND</li>
 *   <li>AGENT_ALREADY_EXISTS(409) → ALREADY_EXISTS（语义更精确，对齐 Plan 08 Red-1）</li>
 *   <li>AGENT_STATUS_CONFLICT(409) → FAILED_PRECONDITION</li>
 *   <li>PARAM_INVALID(400) / VALIDATION_FAILED(400) → INVALID_ARGUMENT</li>
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
            // Plan 08 T4 Red-1: AGENT_ALREADY_EXISTS → ALREADY_EXISTS（语义精确）
            if (ec == ErrorCode.AGENT_ALREADY_EXISTS) {
                return Status.ALREADY_EXISTS.withDescription(ec.getCode() + ": " + be.getMessage());
            }
            return switch (ec.getHttpStatus()) {
                case 404 -> Status.NOT_FOUND.withDescription(ec.getCode() + ": " + be.getMessage());
                case 409 -> Status.FAILED_PRECONDITION.withDescription(ec.getCode() + ": " + be.getMessage());
                case 429 -> Status.RESOURCE_EXHAUSTED.withDescription(ec.getCode() + ": " + be.getMessage());
                case 400 -> Status.INVALID_ARGUMENT.withDescription(ec.getCode() + ": " + be.getMessage());
                default -> Status.INTERNAL.withDescription(ec.getCode() + ": " + be.getMessage());
            };
        }
        return Status.INTERNAL.withDescription("INTERNAL: " + t.getMessage());
    }
}
