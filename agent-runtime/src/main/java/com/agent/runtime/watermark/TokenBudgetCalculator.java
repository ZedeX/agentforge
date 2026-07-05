package com.agent.runtime.watermark;

import com.agent.runtime.api.dto.ModelMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token budget calculator (T8, doc 06-runtime §3.3 / §4.2).
 *
 * <p>Estimates token counts for prompts and messages without external tiktoken dependency.
 * Uses a simple char-based heuristic: 1 token ≈ 4 chars (English) / 1.5 chars (CJK).
 * For mixed content, defaults to ~3 chars/token conservative estimate.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code ThinkPhase} to estimate prompt token cost before calling model gateway</li>
 *   <li>{@code TokenWatermarkMonitor} to check budget thresholds</li>
 *   <li>{@code SessionManager} to track cumulative usage</li>
 * </ul>
 */
@Component
public class TokenBudgetCalculator {

    /** Conservative chars-per-token ratio for mixed content (English + CJK + JSON). */
    private static final double CHARS_PER_TOKEN = 3.0;

    /** CJK detection threshold (Unicode block range). */
    private static final char CJK_START = '\u4E00';
    private static final char CJK_END = '\u9FFF';

    /**
     * Estimate token count for a plain text string.
     *
     * @param text input text (may be null)
     * @return estimated token count (ceiling)
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cjkChars = countCjkChars(text);
        int otherChars = text.length() - cjkChars;
        // CJK: ~1.5 chars/token; other: ~4 chars/token; weighted average
        double tokens = cjkChars / 1.5 + otherChars / 4.0;
        return (int) Math.ceil(tokens);
    }

    /**
     * Estimate token count for a list of chat messages (sum of role + content).
     *
     * @param messages chat messages
     * @return estimated total token count
     */
    public int estimateTokens(List<ModelMessage> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        int total = 0;
        for (ModelMessage msg : messages) {
            // role token overhead (~1 token) + content tokens
            total += 1;
            if (msg.getContent() != null) {
                total += estimateTokens(msg.getContent());
            }
            if (msg.getToolCallId() != null) {
                total += estimateTokens(msg.getToolCallId());
            }
        }
        // conversation overhead (~3 tokens for message boundaries)
        return total + 3;
    }

    /**
     * Calculate usage ratio (used / budget).
     *
     * @param used   tokens used so far
     * @param budget total token budget
     * @return ratio in [0.0, +∞); returns 0.0 if budget <= 0
     */
    public double usageRatio(long used, long budget) {
        if (budget <= 0) return 0.0;
        return (double) used / budget;
    }

    /**
     * Calculate remaining token budget.
     *
     * @param used   tokens used so far
     * @param budget total token budget
     * @return remaining tokens (>= 0 if within budget, negative if exceeded)
     */
    public long remaining(long used, long budget) {
        return budget - used;
    }

    /** Count CJK characters in text (for refined token estimation). */
    private int countCjkChars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= CJK_START && c <= CJK_END) {
                count++;
            }
        }
        return count;
    }
}
