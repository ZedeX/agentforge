package com.agent.hallucination.enums;

/**
 * Self-check result for Layer 2 step-level hallucination detection.
 */
public enum SelfCheckResult {

    /** Claim has source tag and passes self-check. */
    PASS,
    /** Claim lacks source tag or contains suspected hallucination. */
    SUSPECTED,
    /** Insufficient info, agent should refuse to answer. */
    REFUSE
}
