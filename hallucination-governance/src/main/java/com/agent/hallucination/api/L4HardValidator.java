package com.agent.hallucination.api;

/**
 * Layer 4 L4-1 hard validator port (F10 L4: rule-based hard validation fallback).
 *
 * <p>Rules (doc 11 F9.D2):</p>
 * <ul>
 *   <li>Output must contain source tag `[来源:xxx]`.</li>
 *   <li>JSON Schema must be valid.</li>
 *   <li>No blacklist keywords (绝对/100%/保证).</li>
 * </ul>
 */
public interface L4HardValidator {

    /**
     * Validate output against hard rules.
     *
     * @return true when all rules pass; false when violation detected.
     */
    boolean validate(String output);
}
