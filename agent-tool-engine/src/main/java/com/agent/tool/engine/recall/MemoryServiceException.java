package com.agent.tool.engine.recall;

/**
 * Thrown by {@link MemoryServiceClient} when the agent-memory gRPC service is
 * unavailable, times out, or returns an error.
 *
 * <p>Carries the gRPC status code name (e.g. {@code UNAVAILABLE},
 * {@code DEADLINE_EXCEEDED}) so that {@code ToolSemanticRecallerImpl} can
 * decide whether to fall back to keyword matching.</p>
 */
public class MemoryServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** gRPC Status code name, e.g. {@code UNAVAILABLE}, {@code DEADLINE_EXCEEDED}. */
    private final String grpcStatus;

    public MemoryServiceException(String grpcStatus, String message) {
        super(message);
        this.grpcStatus = grpcStatus;
    }

    public MemoryServiceException(String grpcStatus, String message, Throwable cause) {
        super(message, cause);
        this.grpcStatus = grpcStatus;
    }

    public String getGrpcStatus() {
        return grpcStatus;
    }

    /** @return {@code true} if the failure is recoverable (service down / timeout). */
    public boolean isRecoverable() {
        return "UNAVAILABLE".equals(grpcStatus)
                || "DEADLINE_EXCEEDED".equals(grpcStatus)
                || "UNKNOWN".equals(grpcStatus);
    }
}
