package com.agent.tool.engine.model;

/**
 * Result of cleaning a raw tool output (T11).
 *
 * <p>Immutable value object returned by {@code ResultCleaner.clean(String)}.
 * Carries both the cleaned content and metadata for observability:
 * how large the original was, how much was truncated, and how many PII
 * matches were redacted.</p>
 *
 * <p>For backward compatibility with the legacy {@code clean(String, int)}
 * API, callers that only need the content can use {@link #getContent()}.</p>
 */
public class CleanedResult {

    /** Cleaned (and possibly truncated) output text. Never null. */
    private final String content;

    /** Original UTF-8 byte length of the input. */
    private final int originalBytes;

    /** Number of UTF-8 bytes dropped by truncation (0 if not truncated). */
    private final int truncatedBytes;

    /** Number of PII matches redacted (phone + email + api key + id card). */
    private final int redactionCount;

    public CleanedResult(String content, int originalBytes,
                         int truncatedBytes, int redactionCount) {
        this.content = content != null ? content : "";
        this.originalBytes = originalBytes;
        this.truncatedBytes = truncatedBytes;
        this.redactionCount = redactionCount;
    }

    /** Convenience factory for the no-op case (input already clean). */
    public static CleanedResult ofClean(String content, int originalBytes, int redactionCount) {
        return new CleanedResult(content, originalBytes, 0, redactionCount);
    }

    /** Convenience factory for the empty-input case. */
    public static CleanedResult empty() {
        return new CleanedResult("", 0, 0, 0);
    }

    public String getContent() {
        return content;
    }

    public int getOriginalBytes() {
        return originalBytes;
    }

    public int getTruncatedBytes() {
        return truncatedBytes;
    }

    public int getRedactionCount() {
        return redactionCount;
    }

    public boolean isTruncated() {
        return truncatedBytes > 0;
    }

    @Override
    public String toString() {
        return "CleanedResult{contentBytes=" + content.length()
                + ", originalBytes=" + originalBytes
                + ", truncatedBytes=" + truncatedBytes
                + ", redactionCount=" + redactionCount + '}';
    }
}
