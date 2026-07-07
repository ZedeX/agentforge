package com.agent.hallucination.exception;

import lombok.Getter;

/**
 * Base exception for hallucination-governance module.
 */
@Getter
public class HallucinationException extends RuntimeException {

    private final HallucinationErrorCode errorCode;

    public HallucinationException(HallucinationErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public HallucinationException(HallucinationErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public HallucinationException(HallucinationErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
