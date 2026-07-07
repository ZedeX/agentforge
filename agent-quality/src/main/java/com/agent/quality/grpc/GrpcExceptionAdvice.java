package com.agent.quality.grpc;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.quality.exception.BadcasePersistenceException;
import com.agent.quality.exception.L4ValidationException;
import com.agent.quality.exception.QualityPersistenceException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

/**
 * gRPC 异常翻译器（对齐 agent-memory GrpcExceptionAdvice 模式）。
 *
 * <p>将 {@link BusinessException} 及 quality 模块特有异常翻译为 gRPC {@link Status}
 * 并通过 {@code observer.onError} 下发，避免服务方法抛未捕获异常导致 channel 异常断开。</p>
 *
 * <p>错误码 → gRPC Status 映射：</p>
 * <ul>
 *   <li>L4ValidationException → FAILED_PRECONDITION</li>
 *   <li>BadcasePersistenceException → INTERNAL</li>
 *   <li>QualityPersistenceException → INTERNAL</li>
 *   <li>BusinessException → 按 httpStatus 映射 (404→NOT_FOUND, 400→INVALID_ARGUMENT, etc.)</li>
 *   <li>IllegalArgumentException → INVALID_ARGUMENT</li>
 *   <li>默认 → INTERNAL</li>
 * </ul>
 */
@Component
public class GrpcExceptionAdvice {

    /**
     * 将 Throwable 翻译为 gRPC Status 并通过 observer.onError 下发。
     *
     * @param t        业务异常或其他异常
     * @param observer gRPC 响应观察者
     * @param <T>      响应类型
     */
    public <T> void translate(Throwable t, StreamObserver<T> observer) {
        Status status = toStatus(t);
        observer.onError(status.asRuntimeException());
    }

    /**
     * 将 Throwable 翻译为 gRPC Status（含 Description）。
     */
    private Status toStatus(Throwable t) {
        if (t instanceof L4ValidationException) {
            return Status.FAILED_PRECONDITION.withDescription("FAILED_PRECONDITION: " + t.getMessage());
        }
        if (t instanceof BadcasePersistenceException) {
            return Status.INTERNAL.withDescription("INTERNAL: " + t.getMessage());
        }
        if (t instanceof QualityPersistenceException) {
            return Status.INTERNAL.withDescription("INTERNAL: " + t.getMessage());
        }
        if (t instanceof BusinessException be) {
            ErrorCode ec = be.getErrorCode();
            return switch (ec.getHttpStatus()) {
                case 404 -> Status.NOT_FOUND.withDescription(ec.getCode() + ": " + be.getMessage());
                case 409 -> Status.FAILED_PRECONDITION.withDescription(ec.getCode() + ": " + be.getMessage());
                case 429 -> Status.RESOURCE_EXHAUSTED.withDescription(ec.getCode() + ": " + be.getMessage());
                case 400 -> Status.INVALID_ARGUMENT.withDescription(ec.getCode() + ": " + be.getMessage());
                case 503 -> Status.UNAVAILABLE.withDescription(ec.getCode() + ": " + be.getMessage());
                case 504 -> Status.DEADLINE_EXCEEDED.withDescription(ec.getCode() + ": " + be.getMessage());
                default -> Status.INTERNAL.withDescription(ec.getCode() + ": " + be.getMessage());
            };
        }
        if (t instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription("INVALID_ARGUMENT: " + t.getMessage());
        }
        return Status.INTERNAL.withDescription("INTERNAL: " + t.getMessage());
    }
}
