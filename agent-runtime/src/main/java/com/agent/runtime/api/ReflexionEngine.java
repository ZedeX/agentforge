package com.agent.runtime.api;

import com.agent.runtime.model.ReflectionFeedback;
import com.agent.runtime.model.RetryContext;

/**
 * Reflexion retry engine (doc 11-detail-flow F9.D5/D6, PRD §二(五)).
 */
public interface ReflexionEngine {

    /**
     * Inject REFLECTION prompt and retry.
     *
     * @param retryContext retry state
     * @param validationFailure L4 validation failure reason
     * @return reflection feedback for next retry
     */
    ReflectionFeedback retry(RetryContext retryContext, String validationFailure);

    /**
     * Check if retry limit exceeded.
     *
     * @param retryContext retry state
     * @return true if retry_count > max_retry (2)
     */
    boolean isExhausted(RetryContext retryContext);
}
