package com.agent.tool.engine.api;

import com.agent.tool.engine.model.CleanedResult;

/**
 * Result cleaner port (F8 output processing).
 *
 * <p>Two API surfaces:
 * <ul>
 *   <li>{@link #clean(String)} — full clean pipeline returning a
 *       {@link CleanedResult} with metadata. Uses the configured
 *       {@code tool.cleaner.maxBytes} budget.</li>
 *   <li>{@link #clean(String, int)} — legacy API kept for backward
 *       compatibility with callers (e.g. {@code ToolGatewayImpl}) that
 *       pass an explicit token budget. Returns only the content.</li>
 * </ul>
 * </p>
 */
public interface ResultCleaner {

    /**
     * Full clean pipeline: strip ANSI → redact PII → truncate to
     * configured {@code tool.cleaner.maxBytes} → trim trailing whitespace.
     *
     * @param rawOutput raw tool output (may be null)
     * @return {@link CleanedResult} with cleaned content + metadata; never null
     */
    CleanedResult clean(String rawOutput);

    /**
     * Legacy convenience API: clean and truncate to {@code maxToken} tokens
     * (1 token ≈ 4 chars). Returns only the content, no metadata.
     *
     * <p>Kept for backward compatibility; new callers should use
     * {@link #clean(String)}.</p>
     *
     * @param rawOutput raw tool output
     * @param maxToken  max token budget (1 token ≈ 4 chars)
     * @return cleaned output string; never null (empty string for null/empty input)
     */
    String clean(String rawOutput, int maxToken);
}
