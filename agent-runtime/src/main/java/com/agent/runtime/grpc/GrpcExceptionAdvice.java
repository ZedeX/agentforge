package com.agent.runtime.grpc;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC exception translator for AgentRuntime service (T10, doc 06 §7).
 *
 * <p>Maps {@link BusinessException} → gRPC {@link Status} based on {@link ErrorCode#getHttpStatus()},
 * consistent with agent-memory / agent-task-orchestrator pattern.</p>
 *
 * <p>Mapping rules:</p>
 * <ul>
 *   <li>404 → NOT_FOUND (AGENT_NOT_FOUND)</li>
 *   <li>409 → FAILED_PRECONDITION (AGENT_STATUS_CONFLICT, AGENT_ALREADY_EXISTS)</li>
 *   <li>429 → RESOURCE_EXHAUSTED (QUOTA_EXCEEDED, COST_BUDGET_EXCEEDED)</li>
 *   <li>400 → INVALID_ARGUMENT (PARAM_INVALID, VALIDATION_FAILED)</li>
 *   <li>503 → UNAVAILABLE (DEPENDENCY_DOWN, CIRCUIT_OPEN)</li>
 *   <li>504 → DEADLINE_EXCEEDED (TIMEOUT, MODEL_TIMEOUT, TOOL_TIMEOUT)</li>
 *   <li>500/other → INTERNAL</li>
 * </ul>
 *
 * <p>Uses @Component + manual {@link #translate} call pattern (same as agent-memory).</p>
 */
@Component
public class GrpcExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionAdvice.class);

    /**
     * Translate throwable to gRPC Status and send via observer.onError.
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
