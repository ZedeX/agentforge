package com.agent.planning.exception;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;

/**
 * Planning module business exception.
 *
 * <p>Wraps {@link PlanningErrorCode} into a {@link BusinessException}
 * for unified gRPC exception translation via {@link com.agent.planning.grpc.GrpcExceptionAdvice}.</p>
 */
public class PlanningException extends BusinessException {

    private final PlanningErrorCode planningErrorCode;

    public PlanningException(PlanningErrorCode planningErrorCode) {
        super(toErrorCode(planningErrorCode), planningErrorCode.getDefaultMessage());
        this.planningErrorCode = planningErrorCode;
    }

    public PlanningException(PlanningErrorCode planningErrorCode, String message) {
        super(toErrorCode(planningErrorCode), message);
        this.planningErrorCode = planningErrorCode;
    }

    public PlanningException(PlanningErrorCode planningErrorCode, String message, Throwable cause) {
        super(toErrorCode(planningErrorCode), message, cause);
        this.planningErrorCode = planningErrorCode;
    }

    public PlanningErrorCode getPlanningErrorCode() {
        return planningErrorCode;
    }

    /**
     * Map PlanningErrorCode httpStatus to the closest platform ErrorCode.
     */
    private static ErrorCode toErrorCode(PlanningErrorCode pec) {
        return switch (pec.getHttpStatus()) {
            case 404 -> ErrorCode.TASK_NOT_FOUND;
            case 400 -> ErrorCode.VALIDATION_FAILED;
            case 429 -> ErrorCode.RATE_LIMITED;
            case 503 -> ErrorCode.DEPENDENCY_DOWN;
            case 504 -> ErrorCode.TIMEOUT;
            default -> ErrorCode.INTERNAL;
        };
    }
}
