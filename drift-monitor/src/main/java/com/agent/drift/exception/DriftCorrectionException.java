package com.agent.drift.exception;

/**
 * Thrown when drift correction fails.
 */
public class DriftCorrectionException extends DriftException {

    public DriftCorrectionException(String message) {
        super(message);
    }

    public DriftCorrectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
