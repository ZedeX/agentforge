package com.agent.drift.exception;

/**
 * Base exception for drift-monitor module.
 */
public class DriftException extends RuntimeException {

    public DriftException(String message) {
        super(message);
    }

    public DriftException(String message, Throwable cause) {
        super(message, cause);
    }
}
