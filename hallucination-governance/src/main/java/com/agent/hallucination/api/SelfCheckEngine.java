package com.agent.hallucination.api;

import com.agent.hallucination.enums.SelfCheckResult;
import com.agent.hallucination.model.Claim;

/**
 * Layer 2 self-check engine port (F10 L2: step-level hallucination self-check).
 */
public interface SelfCheckEngine {

    /**
     * Check a claim produced by an agent step.
     *
     * <p>Returns SUSPECTED when claim lacks source tag, REFUSE when info insufficient.</p>
     */
    SelfCheckResult check(Claim claim);
}
