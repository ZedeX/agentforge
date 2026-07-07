package com.agent.hallucination.exception;

/**
 * Thrown when hallucination metric/event persistence fails.
 */
public class HallucinationPersistenceException extends HallucinationException {

    public HallucinationPersistenceException(String message) {
        super(HallucinationErrorCode.METRIC_WRITE_FAILED, message);
    }

    public HallucinationPersistenceException(String message, Throwable cause) {
        super(HallucinationErrorCode.METRIC_WRITE_FAILED, message, cause);
    }
}
