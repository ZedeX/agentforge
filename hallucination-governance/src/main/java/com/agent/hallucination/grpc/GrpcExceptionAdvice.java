package com.agent.hallucination.grpc;

import com.agent.common.exception.BusinessException;
import com.agent.hallucination.exception.HallucinationException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC 异常翻译器（F10 幻觉治理，复用 agent-memory 模式）。
 *
 * <p>将 {@link BusinessException} / {@link HallucinationException} 翻译为 gRPC {@link Status}
 * 并通过 {@code observer.onError} 下发，避免服务方法抛未捕获异常导致 channel 异常断开。</p>
 *
 * <p>错误码 → gRPC Status 映射：</p>
 * <ul>
 *   <li>404 → NOT_FOUND</li>
 *   <li>403 → PERMISSION_DENIED</li>
 *   <li>400 → INVALID_ARGUMENT</li>
 *   <li>409 → FAILED_PRECONDITION</li>
 *   <li>429 → RESOURCE_EXHAUSTED</li>
 *   <li>503 → UNAVAILABLE</li>
 *   <li>504 → DEADLINE_EXCEEDED</li>
 *   <li>其他 500 → INTERNAL</li>
 * </ul>
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
        if (t instanceof HallucinationException he) {
            return mapHallucinationException(he);
        }
        if (t instanceof BusinessException be) {
            return mapBusinessException(be);
        }
        if (t instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription("INVALID_ARGUMENT: " + t.getMessage());
        }
        return Status.INTERNAL.withDescription("INTERNAL: " + t.getMessage());
    }

    private Status mapHallucinationException(HallucinationException he) {
        int httpStatus = he.getErrorCode().getHttpStatus();
        String desc = he.getErrorCode().getCode() + ": " + he.getMessage();
        return switch (httpStatus) {
            case 404 -> Status.NOT_FOUND.withDescription(desc);
            case 403 -> Status.PERMISSION_DENIED.withDescription(desc);
            case 400 -> Status.INVALID_ARGUMENT.withDescription(desc);
            case 409 -> Status.FAILED_PRECONDITION.withDescription(desc);
            case 429 -> Status.RESOURCE_EXHAUSTED.withDescription(desc);
            case 503 -> Status.UNAVAILABLE.withDescription(desc);
            case 504 -> Status.DEADLINE_EXCEEDED.withDescription(desc);
            default -> Status.INTERNAL.withDescription(desc);
        };
    }

    private Status mapBusinessException(BusinessException be) {
        int httpStatus = be.getErrorCode().getHttpStatus();
        String desc = be.getErrorCode().getCode() + ": " + be.getMessage();
        return switch (httpStatus) {
            case 404 -> Status.NOT_FOUND.withDescription(desc);
            case 409 -> Status.FAILED_PRECONDITION.withDescription(desc);
            case 429 -> Status.RESOURCE_EXHAUSTED.withDescription(desc);
            case 400 -> Status.INVALID_ARGUMENT.withDescription(desc);
            case 503 -> Status.UNAVAILABLE.withDescription(desc);
            case 504 -> Status.DEADLINE_EXCEEDED.withDescription(desc);
            default -> Status.INTERNAL.withDescription(desc);
        };
    }
}
