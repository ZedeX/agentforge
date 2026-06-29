package com.agent.runtime.model;

import com.agent.runtime.enums.ReflexionResult;

/**
 * Reflexion retry context (doc 11-detail-flow F9.D5/D6).
 */
public class RetryContext {

    private int retryCount;
    private int maxRetry;
    private String failureReason;
    private ReflexionResult result;

    public RetryContext() {
        this.retryCount = 0;
        this.maxRetry = 2;
    }

    public RetryContext(int retryCount, int maxRetry) {
        this.retryCount = retryCount;
        this.maxRetry = maxRetry;
    }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public void incrementRetryCount() { this.retryCount++; }

    public int getMaxRetry() { return maxRetry; }
    public void setMaxRetry(int maxRetry) { this.maxRetry = maxRetry; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public ReflexionResult getResult() { return result; }
    public void setResult(ReflexionResult result) { this.result = result; }
}
