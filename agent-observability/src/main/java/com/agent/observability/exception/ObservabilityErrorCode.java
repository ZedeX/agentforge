package com.agent.observability.exception;

/**
 * Observability error codes (aligned with agent-common ErrorCode pattern).
 */
public enum ObservabilityErrorCode {

    TRACE_NOT_FOUND("OBS_TRACE_NOT_FOUND", 404, "Trace not found"),
    METRIC_NOT_FOUND("OBS_METRIC_NOT_FOUND", 404, "Metric not found"),
    SERVICE_NOT_FOUND("OBS_SERVICE_NOT_FOUND", 404, "Service not found"),
    INVALID_TIME_RANGE("OBS_INVALID_TIME_RANGE", 400, "Invalid time range"),
    QUERY_FAILED("OBS_QUERY_FAILED", 500, "Query failed"),
    PERSISTENCE_FAILED("OBS_PERSISTENCE_FAILED", 500, "Persistence failed");

    private final String code;
    private final int httpStatus;
    private final String message;

    ObservabilityErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
