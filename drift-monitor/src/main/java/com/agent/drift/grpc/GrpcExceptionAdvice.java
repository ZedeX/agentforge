package com.agent.drift.grpc;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.drift.exception.DriftCorrectionException;
import com.agent.drift.exception.DriftDetectionException;
import com.agent.drift.exception.DriftException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

/**
 * gRPC exception translator for drift-monitor module.
 *
 * <p>Translates {@link BusinessException} and {@link DriftException}
 * to gRPC {@link Status} and delivers via {@code observer.onError}.</p>
 */
@Component
public class GrpcExceptionAdvice {

    /**
     * Translate throwable to gRPC Status and deliver via observer.onError.
     */
    public <T> void translate(Throwable t, StreamObserver<T> observer) {
        Status status = toStatus(t);
        observer.onError(status.asRuntimeException());
    }

    private Status toStatus(Throwable t) {
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
        if (t instanceof DriftDetectionException) {
            return Status.INTERNAL.withDescription("DRIFT_DETECTION_ERROR: " + t.getMessage());
        }
        if (t instanceof DriftCorrectionException) {
            return Status.INTERNAL.withDescription("DRIFT_CORRECTION_ERROR: " + t.getMessage());
        }
        if (t instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription("INVALID_ARGUMENT: " + t.getMessage());
        }
        return Status.INTERNAL.withDescription("INTERNAL: " + t.getMessage());
    }
}
