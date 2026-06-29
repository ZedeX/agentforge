package com.agent.hallucination.enums;

/**
 * Tool gateway guard result for Layer 5 param validation.
 */
public enum GuardResult {

    /** Params match schema, allow tool call. */
    ALLOWED,
    /** Params schema mismatch, block tool call. */
    REJECTED
}
