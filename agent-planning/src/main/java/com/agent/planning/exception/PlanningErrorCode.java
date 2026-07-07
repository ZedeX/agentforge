package com.agent.planning.exception;

/**
 * Planning module specific error codes.
 *
 * <p>Extends the platform ErrorCode with planning-domain-specific codes.
 * Each code carries an HTTP status for gRPC translation.</p>
 */
public enum PlanningErrorCode {

    PLAN_NOT_FOUND("PLAN_NOT_FOUND", 404, "Plan not found"),
    INVALID_DAG("INVALID_DAG", 400, "Invalid DAG structure"),
    REPLAN_EXHAUSTED("REPLAN_EXHAUSTED", 500, "Replan attempts exhausted"),
    TEMPLATE_MATCH_FAILED("TEMPLATE_MATCH_FAILED", 500, "Template matching failed"),
    COMPLEXITY_ASSESSMENT_FAILED("COMPLEXITY_ASSESSMENT_FAILED", 500, "Complexity assessment failed");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    PlanningErrorCode(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
