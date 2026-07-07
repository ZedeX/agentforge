package com.agent.observability.grpc;

import com.agent.common.exception.BusinessException;
import com.agent.observability.exception.ObservabilityException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC exception translator (observability module, reuses agent-memory pattern).
 *
 * <p>Translates {@link BusinessException} / {@link ObservabilityException} into gRPC {@link Status}
 * via {@code observer.onError} to prevent channel disconnection.</p>
 */
@Component
public class GrpcExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionAdvice.class);

    /**
     * Translate throwable to gRPC Status and deliver via observer.onError.
     * S-12: All gRPC exceptions are logged with status code + description + full stack trace.
     */
    public <T> void translate(Throwable t, StreamObserver<T> observer) {
        Status status = toStatus(t);
        if (log.isWarnEnabled()) {
            log.warn("gRPC exception -> status={} desc={}", status.getCode(),
                    status.getDescription(), t);
        }
        observer.onError(status.asRuntimeException());
    }

    private Status toStatus(Throwable t) {
        if (t instanceof ObservabilityException oe) {
            return mapObservabilityException(oe);
        }
        if (t instanceof BusinessException be) {
            return mapBusinessException(be);
        }
        if (t instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription("INVALID_ARGUMENT: " + t.getMessage());
        }
        return Status.INTERNAL.withDescription("INTERNAL: " + t.getMessage());
    }

    private Status mapObservabilityException(ObservabilityException oe) {
        int httpStatus = oe.getErrorCode().getHttpStatus();
        String desc = oe.getErrorCode().getCode() + ": " + oe.getMessage();
        return switch (httpStatus) {
            case 404 -> Status.NOT_FOUND.withDescription(desc);
            case 400 -> Status.INVALID_ARGUMENT.withDescription(desc);
            case 429 -> Status.RESOURCE_EXHAUSTED.withDescription(desc);
            case 503 -> Status.UNAVAILABLE.withDescription(desc);
            default -> Status.INTERNAL.withDescription(desc);
        };
    }

    private Status mapBusinessException(BusinessException be) {
        int httpStatus = be.getErrorCode().getHttpStatus();
        String desc = be.getErrorCode().getCode() + ": " + be.getMessage();
        return switch (httpStatus) {
            case 404 -> Status.NOT_FOUND.withDescription(desc);
            case 409 -> Status.FAILED_PRECONDITION.withDescription(desc);
            case 400 -> Status.INVALID_ARGUMENT.withDescription(desc);
            case 429 -> Status.RESOURCE_EXHAUSTED.withDescription(desc);
            case 503 -> Status.UNAVAILABLE.withDescription(desc);
            default -> Status.INTERNAL.withDescription(desc);
        };
    }
}
