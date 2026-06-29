package com.agent.runtime.exception;

/**
 * Thrown when Reflexion retry count exceeds max limit (2).
 * Ref: doc 11-detail-flow F9.D6 false branch, UT-RT-006.
 */
public class MaxRetryExceededException extends RuntimeException {

    private final int retryCount;

    public MaxRetryExceededException(int retryCount) {
        super("MAX_RETRY_EXCEEDED: retry_count=" + retryCount + " > max=2, transfer to manual review");
        this.retryCount = retryCount;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
