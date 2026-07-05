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
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC exception translator for the ToolGateway service (T12).
 *
 * <p>Converts {@link ToolEngineException} subclasses into gRPC {@link Status}
 * codes and propagates via {@code observer.onError}. Keeps the service methods
 * clean — they throw domain exceptions, the advice handles wire-level mapping.</p>
 *
 * <p>Mapping table (aligned with doc 05 §12.4 + Plan 05 T12):
 * <ul>
 *   <li>{@link ToolNotFoundException} (404) → {@link Status.Code#NOT_FOUND}</li>
 *   <li>{@link ToolDisabledException} (403) → {@link Status.Code#PERMISSION_DENIED}</li>
 *   <li>{@link ToolApprovalException} (403) → {@link Status.Code#PERMISSION_DENIED}
 *       (with metadata approval_call_id when available)</li>
 *   <li>{@link ToolValidationException} (400) → {@link Status.Code#INVALID_ARGUMENT}</li>
 *   <li>{@link ToolQuotaExhaustedException} (429) → {@link Status.Code#RESOURCE_EXHAUSTED}</li>
 *   <li>{@link ToolExecutionTimeoutException} (504) → {@link Status.Code#DEADLINE_EXCEEDED}</li>
 *   <li>{@link ToolSandboxFailureException} (500) → {@link Status.Code#INTERNAL}</li>
 *   <li>{@link ToolEngineException} base (other) → {@link Status.Code#INTERNAL}
 *       with errorCode in description</li>
 *   <li>Other {@link Throwable} → {@link Status.Code#INTERNAL}</li>
 * </ul>
 * </p>
 *
 * <p>Design: follows the agent-memory / agent-repo / agent-task-orchestrator
 * pattern — manual {@code translate(throwable, observer)} call rather than
 * net.devh's {@code @GrpcAdvice} annotation, for explicit onError timing.</p>
 */
@Component
public class GrpcExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionAdvice.class);

    /**
     * Translate a throwable into a gRPC status and propagate via
     * {@code observer.onError}.
     *
     * @param t        the exception thrown by the service method
     * @param observer the response observer to fail
     * @param <T>      response message type
     */
    public <T> void translate(Throwable t, StreamObserver<T> observer) {
        Status status = toStatus(t);
        if (log.isWarnEnabled()) {
            log.warn("gRPC exception -> status={} desc={}", status.getCode(),
                    status.getDescription(), t);
        }
        observer.onError(status.asRuntimeException());
    }

    /** Exposed for unit tests: convert throwable to gRPC Status without propagating. */
    Status toStatus(Throwable t) {
        if (t instanceof ToolNotFoundException) {
            return Status.NOT_FOUND
                    .withDescription(errLabel((ToolEngineException) t));
        }
        if (t instanceof ToolDisabledException) {
            return Status.PERMISSION_DENIED
                    .withDescription(errLabel((ToolEngineException) t));
        }
        if (t instanceof ToolApprovalException) {
            // R3 approval missing/expired → 403 PERMISSION_DENIED
            return Status.PERMISSION_DENIED
                    .withDescription(errLabel((ToolEngineException) t));
        }
        if (t instanceof ToolValidationException) {
            return Status.INVALID_ARGUMENT
                    .withDescription(errLabel((ToolEngineException) t));
        }
        if (t instanceof ToolQuotaExhaustedException) {
            return Status.RESOURCE_EXHAUSTED
                    .withDescription(errLabel((ToolEngineException) t));
        }
        if (t instanceof ToolExecutionTimeoutException) {
            return Status.DEADLINE_EXCEEDED
                    .withDescription(errLabel((ToolEngineException) t));
        }
        if (t instanceof ToolSandboxFailureException) {
            return Status.INTERNAL
                    .withDescription(errLabel((ToolEngineException) t));
        }
        if (t instanceof ToolEngineException) {
            // Base ToolEngineException → INTERNAL with errorCode
            return Status.INTERNAL
                    .withDescription(errLabel((ToolEngineException) t));
        }
        if (t instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT
                    .withDescription("INVALID_ARGUMENT: " + t.getMessage());
        }
        return Status.INTERNAL
                .withDescription("INTERNAL: " + t.getMessage());
    }

    /** Build a "{errorCode}: {message}" label for the Status description. */
    private static String errLabel(ToolEngineException e) {
        return e.getErrorCode() + ": " + e.getMessage();
    }
}
