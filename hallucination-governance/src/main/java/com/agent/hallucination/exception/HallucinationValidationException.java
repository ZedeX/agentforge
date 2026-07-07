package com.agent.hallucination.exception;

/**
 * Thrown when hallucination governance validation fails (e.g. invalid input, missing fields).
 */
public class HallucinationValidationException extends HallucinationException {

    public HallucinationValidationException(String message) {
        super(HallucinationErrorCode.INVALID_CLAIM, message);
    }

    public HallucinationValidationException(String message, Throwable cause) {
        super(HallucinationErrorCode.INVALID_CLAIM, message, cause);
    }
}
