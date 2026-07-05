package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ResultCleaner;
import com.agent.tool.engine.cleaner.AnsiStripper;
import com.agent.tool.engine.cleaner.ByteTruncator;
import com.agent.tool.engine.cleaner.PiiRedactor;
import com.agent.tool.engine.config.ToolEngineProperties;
import com.agent.tool.engine.model.CleanedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * F8 output cleaner implementation (T11).
 *
 * <p>Pipeline (strict order — each stage sees the output of the previous):
 * <ol>
 *   <li><b>strip ANSI</b>: remove color/cursor escape sequences via {@link AnsiStripper}.</li>
 *   <li><b>redact PII</b>: replace phone / email / api-key / id-card with masks via
 *       {@link PiiRedactor}. Order matters: longer patterns first.</li>
 *   <li><b>truncate</b>: cap UTF-8 byte length at {@code tool.cleaner.maxBytes},
 *       splitting only on character boundaries via {@link ByteTruncator}.</li>
 *   <li><b>trim</b>: remove trailing whitespace (preserving interior newlines).</li>
 * </ol>
 * </p>
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #clean(String)} — full pipeline with default configured budget,
 *       returns {@link CleanedResult} with metadata.</li>
 *   <li>{@link #clean(String, int)} — legacy API for callers passing an explicit
 *       token budget (1 token ≈ 4 chars). Returns only the content.</li>
 * </ul>
 * </p>
 */
@Component
public class ResultCleanerImpl implements ResultCleaner {

    private static final Logger log = LoggerFactory.getLogger(ResultCleanerImpl.class);

    /** 1 token ≈ 4 chars (rough estimate for legacy API). */
    private static final int CHARS_PER_TOKEN = 4;

    private final AnsiStripper ansiStripper;
    private final PiiRedactor piiRedactor;
    private final ToolEngineProperties properties;

    /**
     * Spring constructor.
     *
     * @param ansiStripper ANSI escape stripper (always present)
     * @param piiRedactor  PII redactor (always present)
     * @param properties   tool engine properties (read {@code tool.cleaner.maxBytes})
     */
    public ResultCleanerImpl(AnsiStripper ansiStripper,
                             PiiRedactor piiRedactor,
                             ToolEngineProperties properties) {
        this.ansiStripper = ansiStripper;
        this.piiRedactor = piiRedactor;
        this.properties = properties;
    }

    /** Test constructor: defaults to a 8192-byte budget when properties are unavailable. */
    public ResultCleanerImpl() {
        this(new AnsiStripper(), new PiiRedactor(), defaultProperties());
    }

    private static ToolEngineProperties defaultProperties() {
        ToolEngineProperties props = new ToolEngineProperties();
        props.getCleaner().setMaxBytes(8192);
        return props;
    }

    @Override
    public CleanedResult clean(String rawOutput) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return CleanedResult.empty();
        }
        int originalBytes = ByteTruncator.byteLength(rawOutput);
        int maxBytes = properties.getCleaner().getMaxBytes();
        if (maxBytes <= 0) {
            log.warn("tool.cleaner.maxBytes={} 非法, 直接返回空串", maxBytes);
            return CleanedResult.empty();
        }

        // Step 1: strip ANSI
        String ansiStripped = ansiStripper.strip(rawOutput);

        // Step 2: redact PII (and count replacements)
        RedactResult redacted = redactWithCount(ansiStripped);

        // Step 3: truncate to maxBytes (UTF-8 char-boundary safe)
        String truncated = ByteTruncator.truncate(redacted.text, maxBytes);
        int truncatedBytes = ByteTruncator.byteLength(redacted.text)
                - ByteTruncator.byteLength(truncated);
        if (truncatedBytes < 0) {
            // ByteTruncator appends a suffix that may make the result slightly
            // longer than the prefix alone; truncatedBytes is the bytes dropped
            // from the original redacted text, so use the redacted length minus
            // the kept prefix length.
            truncatedBytes = Math.max(0, ByteTruncator.byteLength(redacted.text)
                    - ByteTruncator.byteLength(truncated));
        }

        // Step 4: trim trailing whitespace (preserve interior newlines)
        String trimmed = truncated.stripTrailing();

        log.debug("clean: originalBytes={}, redactionCount={}, truncatedBytes={}, finalBytes={}",
                originalBytes, redacted.count, truncatedBytes,
                ByteTruncator.byteLength(trimmed));

        return new CleanedResult(trimmed, originalBytes, truncatedBytes, redacted.count);
    }

    @Override
    public String clean(String rawOutput, int maxToken) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return "";
        }
        if (maxToken <= 0) {
            log.warn("maxToken={} 非法, 直接返回空串", maxToken);
            return "";
        }

        // Run the same pipeline but with maxToken * 4 as the byte budget.
        int maxBytes = maxToken * CHARS_PER_TOKEN;

        // Step 1: strip ANSI
        String ansiStripped = ansiStripper.strip(rawOutput);

        // Step 2: redact PII
        String redacted = piiRedactor.redact(ansiStripped);

        // Step 3: truncate
        String truncated = ByteTruncator.truncate(redacted, maxBytes);

        // Step 4: trim trailing whitespace
        return truncated.stripTrailing();
    }

    /**
     * Apply all four PII patterns and count total replacements.
     *
     * <p>Order: ID card (18 chars) → API key (40+ chars) → phone (11 chars)
     * → email. Each pattern is counted on the un-redacted text before
     * replacement so the count reflects actual matches.</p>
     */
    private RedactResult redactWithCount(String text) {
        if (text == null || text.isEmpty()) {
            return new RedactResult(text, 0);
        }
        int total = 0;
        String masked = text;

        int n = countMatches(PiiRedactor.ID_CARD_PATTERN, masked);
        masked = PiiRedactor.ID_CARD_PATTERN.matcher(masked).replaceAll(PiiRedactor.ID_CARD_MASK);
        total += n;

        n = countMatches(PiiRedactor.API_KEY_PATTERN, masked);
        masked = PiiRedactor.API_KEY_PATTERN.matcher(masked).replaceAll(PiiRedactor.API_KEY_MASK);
        total += n;

        n = countMatches(PiiRedactor.PHONE_PATTERN, masked);
        masked = PiiRedactor.PHONE_PATTERN.matcher(masked).replaceAll(PiiRedactor.PHONE_MASK);
        total += n;

        n = countMatches(PiiRedactor.EMAIL_PATTERN, masked);
        masked = PiiRedactor.EMAIL_PATTERN.matcher(masked).replaceAll(PiiRedactor.EMAIL_MASK);
        total += n;

        return new RedactResult(masked, total);
    }

    private static int countMatches(java.util.regex.Pattern pattern, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Matcher m = pattern.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    /** Internal: hold redacted text + total redaction count. */
    private static final class RedactResult {
        final String text;
        final int count;

        RedactResult(String text, int count) {
            this.text = text;
            this.count = count;
        }
    }

    /**
     * Batch clean a list of raw outputs using the default configured budget.
     *
     * @param rawOutputs list of raw outputs (may be null or contain nulls)
     * @return list of {@link CleanedResult} in the same order; never null
     */
    public List<CleanedResult> cleanBatch(List<String> rawOutputs) {
        if (rawOutputs == null || rawOutputs.isEmpty()) {
            return List.of();
        }
        List<CleanedResult> results = new ArrayList<>(rawOutputs.size());
        for (String raw : rawOutputs) {
            results.add(clean(raw));
        }
        return results;
    }
}
