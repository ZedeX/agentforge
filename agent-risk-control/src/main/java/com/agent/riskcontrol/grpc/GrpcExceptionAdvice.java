package com.agent.riskcontrol.grpc;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

/**
 * gRPC exception translator for agent-risk-control module.
 *
 * <p>Maps {@link BusinessException} and other exceptions to gRPC {@link Status}
 * and delivers via {@code observer.onError}.
 *
 * <p>Error code -> gRPC Status mapping:
 * <ul>
 *   <li>CONTENT_BLOCKED(400) -> INVALID_ARGUMENT</li>
 *   <li>FORBIDDEN(403) -> PERMISSION_DENIED</li>
 *   <li>PARAM_INVALID(400) / VALIDATION_FAILED(400) -> INVALID_ARGUMENT</li>
 *   <li>INTERNAL(500) -> INTERNAL</li>
 *   <li>Other -> INTERNAL</li>
 * </ul>
 */
@Component
public class GrpcExceptionAdvice {

    /**
     * Translate a Throwable to gRPC Status and deliver via observer.onError.
     *
     * @param t        the exception
     * @param observer gRPC response observer
     * @param <T>      response type
     */
    public <T> void translate(Throwable t, StreamObserver<T> observer) {
        Status status = toStatus(t);
        observer.onError(status.asRuntimeException());
    }

    private Status toStatus(Throwable t) {
        if (t instanceof BusinessException be) {
            ErrorCode ec = be.getErrorCode();
            return switch (ec.getHttpStatus()) {
                case 400 -> Status.INVALID_ARGUMENT.withDescription(ec.getCode() + ": " + be.getMessage());
                case 403 -> Status.PERMISSION_DENIED.withDescription(ec.getCode() + ": " + be.getMessage());
                case 404 -> Status.NOT_FOUND.withDescription(ec.getCode() + ": " + be.getMessage());
                case 429 -> Status.RESOURCE_EXHAUSTED.withDescription(ec.getCode() + ": " + be.getMessage());
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
