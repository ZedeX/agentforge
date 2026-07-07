package com.agent.observability.exception;

import lombok.Getter;

/**
 * Base exception for observability module.
 */
@Getter
public class ObservabilityException extends RuntimeException {

    private final ObservabilityErrorCode errorCode;

    public ObservabilityException(ObservabilityErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ObservabilityException(ObservabilityErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ObservabilityException(ObservabilityErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
