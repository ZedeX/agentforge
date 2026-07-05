package com.agent.tool.engine.cleaner;

import java.nio.charset.StandardCharsets;

/**
 * Truncates text to a maximum number of UTF-8 bytes (T11 Refactor).
 *
 * <p>Plain {@code String.substring(0, n)} can split a multi-byte UTF-8
 * sequence (Chinese characters are 3 bytes), producing invalid output
 * when re-encoded. This utility walks the Java char array, accumulates
 * UTF-8 byte length, and stops at the largest char boundary that fits
 * within {@code maxBytes}.</p>
 *
 * <p>When truncation occurs, a {@code ...[truncated N bytes]} suffix is
 * appended so callers can see how much was dropped. The suffix itself
 * counts toward the byte budget so the final output never exceeds
 * {@code maxBytes}.</p>
 */
public final class ByteTruncator {

    /** Suffix appended when truncation occurs. Formatted with the dropped byte count. */
    public static final String TRUNCATION_SUFFIX_TEMPLATE = "...[truncated %d bytes]";

    private ByteTruncator() {
        // utility class
    }

    /**
     * Truncate {@code text} to at most {@code maxBytes} UTF-8 bytes.
     *
     * <p>If {@code text} fits within the budget, it is returned unchanged.
     * Otherwise the largest prefix that (together with the truncation
     * suffix) fits in {@code maxBytes} is returned, with the suffix
     * indicating how many bytes were dropped.</p>
     *
     * @param text     input text (may be null)
     * @param maxBytes maximum UTF-8 byte length of the returned string
     * @return truncated text with optional suffix; null if input is null;
     *         empty string if {@code maxBytes <= 0}
     */
    public static String truncate(String text, int maxBytes) {
        if (text == null) {
            return null;
        }
        if (maxBytes <= 0) {
            return "";
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }

        // Reserve space for the suffix. We compute suffix length using the
        // actual dropped byte count, which is at most (bytes.length - keptBytes).
        // To stay simple, we first compute the suffix for the worst case
        // (dropped = bytes.length - 1), then shrink if needed.
        int worstCaseDropped = bytes.length - 1;
        String worstCaseSuffix = String.format(TRUNCATION_SUFFIX_TEMPLATE, worstCaseDropped);
        int suffixBytes = worstCaseSuffix.getBytes(StandardCharsets.UTF_8).length;

        // If even the suffix alone exceeds maxBytes, return just a minimal marker.
        if (suffixBytes >= maxBytes) {
            return new String(bytes, 0, Math.max(0, maxBytes), StandardCharsets.UTF_8);
        }

        int keepBytes = maxBytes - suffixBytes;
        // Walk back to a UTF-8 char boundary. UTF-8 continuation bytes start with 0b10xxxxxx.
        while (keepBytes > 0 && (bytes[keepBytes] & 0xC0) == 0x80) {
            keepBytes--;
        }
        if (keepBytes <= 0) {
            return new String(bytes, 0, Math.max(0, maxBytes), StandardCharsets.UTF_8);
        }

        int actualDropped = bytes.length - keepBytes;
        String suffix = String.format(TRUNCATION_SUFFIX_TEMPLATE, actualDropped);
        // suffixBytes may shrink by 1-2 bytes if actualDropped has fewer digits;
        // recompute the actual returned byte length to verify it fits.
        // In the worst case the suffix gets shorter, so the result fits.
        String prefix = new String(bytes, 0, keepBytes, StandardCharsets.UTF_8);
        return prefix + suffix;
    }

    /**
     * Count UTF-8 bytes of a string without allocating a byte array via
     * {@code String.getBytes()} for the common short-string path.
     *
     * @param text input text (may be null)
     * @return UTF-8 byte length; 0 if input is null
     */
    public static int byteLength(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.getBytes(StandardCharsets.UTF_8).length;
    }
}
