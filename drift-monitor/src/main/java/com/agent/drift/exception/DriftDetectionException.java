package com.agent.drift.exception;

/**
 * Thrown when drift detection fails.
 */
public class DriftDetectionException extends DriftException {

    public DriftDetectionException(String message) {
        super(message);
    }

    public DriftDetectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
