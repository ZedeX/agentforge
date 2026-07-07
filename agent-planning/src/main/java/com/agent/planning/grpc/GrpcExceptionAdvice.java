package com.agent.planning.grpc;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.planning.exception.PlanningErrorCode;
import com.agent.planning.exception.PlanningException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC exception translator for agent-planning module.
 *
 * <p>Translates {@link BusinessException} / {@link PlanningException}
 * to gRPC {@link Status} and dispatches via {@code observer.onError},
 * preventing uncaught exceptions from breaking the gRPC channel.</p>
 *
 * <p>Error code to gRPC Status mapping:</p>
 * <ul>
 *   <li>404 (NOT_FOUND) -> NOT_FOUND</li>
 *   <li>400 (VALIDATION_FAILED / PARAM_INVALID) -> INVALID_ARGUMENT</li>
 *   <li>409 (CONFLICT) -> FAILED_PRECONDITION</li>
 *   <li>429 (RATE_LIMITED / QUOTA_EXCEEDED) -> RESOURCE_EXHAUSTED</li>
 *   <li>503 (DEPENDENCY_DOWN / CIRCUIT_OPEN) -> UNAVAILABLE</li>
 *   <li>504 (TIMEOUT) -> DEADLINE_EXCEEDED</li>
 *   <li>other 500 -> INTERNAL</li>
 * </ul>
 */
@Component
public class GrpcExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionAdvice.class);

    /**
     * Translate a Throwable to gRPC Status and dispatch via observer.onError.
     * S-12: All gRPC exceptions are logged with status code + description + full stack trace.
     *
     * @param t        business exception or other throwable
     * @param observer gRPC response observer
     * @param <T>      response type
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
        if (t instanceof PlanningException pe) {
            return planningStatus(pe);
        }
        if (t instanceof BusinessException be) {
            return businessStatus(be);
        }
        if (t instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription("INVALID_ARGUMENT: " + t.getMessage());
        }
        return Status.INTERNAL.withDescription("INTERNAL: " + t.getMessage());
    }

    private Status planningStatus(PlanningException pe) {
        PlanningErrorCode pec = pe.getPlanningErrorCode();
        return switch (pec.getHttpStatus()) {
            case 404 -> Status.NOT_FOUND.withDescription(pec.getCode() + ": " + pe.getMessage());
            case 400 -> Status.INVALID_ARGUMENT.withDescription(pec.getCode() + ": " + pe.getMessage());
            case 429 -> Status.RESOURCE_EXHAUSTED.withDescription(pec.getCode() + ": " + pe.getMessage());
            case 503 -> Status.UNAVAILABLE.withDescription(pec.getCode() + ": " + pe.getMessage());
            case 504 -> Status.DEADLINE_EXCEEDED.withDescription(pec.getCode() + ": " + pe.getMessage());
            default -> Status.INTERNAL.withDescription(pec.getCode() + ": " + pe.getMessage());
        };
    }

    private Status businessStatus(BusinessException be) {
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
}
