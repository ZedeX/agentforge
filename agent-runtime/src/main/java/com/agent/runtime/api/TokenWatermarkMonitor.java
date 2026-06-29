package com.agent.runtime.api;

import com.agent.runtime.enums.TokenLevel;
import com.agent.runtime.model.TokenWatermark;

/**
 * Token watermark monitor (doc 11-detail-flow F7, PRD §二(三)3).
 */
public interface TokenWatermarkMonitor {

    /**
     * Check current token usage level.
     *
     * @param usedTokens used token count
     * @param maxTokens max token window (e.g. 128K)
     * @return TokenLevel (SAFE / WARN / CRITICAL / CIRCUIT_BREAK)
     */
    TokenLevel checkLevel(long usedTokens, long maxTokens);

    /**
     * Compress context based on watermark level.
     *
     * @param watermark current token watermark
     * @return compressed context (light/medium/heavy based on level)
     */
    String compress(TokenWatermark watermark);
}
