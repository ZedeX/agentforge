package com.agent.modelgateway.api;

/**
 * Token counter (doc 02-api §4, PRD §二(二)5 token estimation).
 *
 * <p>Reuses agent-common TokenEstimator baseline; Chinese chars count 1.7x.
 * Skeleton stage: char-based heuristic. Full integration deferred to Plan 07 T10.</p>
 */
public interface TokenCounter {

    /**
     * Estimate token count for the given text.
     *
     * <p>Heuristic: English ~4 char/token, Chinese chars counted 1.7x (each Chinese char ≈ 0.42 token
     * after the 1.7x coefficient applied to the 1-char-per-token base).</p>
     *
     * @param text input text, null/empty returns 0
     * @return estimated token count (≥ 0)
     */
    int count(String text);
}
