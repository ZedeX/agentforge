package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.config.ToolEngineProperties;
import com.agent.tool.engine.model.CleanedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T11 {@link ResultCleanerImpl} unit tests.
 *
 * <p>Covers the full pipeline (strip ANSI → redact PII → truncate → trim)
 * and each stage in isolation, plus edge cases (null / empty / configurable
 * budget) and the legacy {@code clean(String, int)} API.</p>
 */
class ResultCleanerImplTest {

    private ToolEngineProperties properties;
    private ResultCleanerImpl cleaner;

    @BeforeEach
    void setUp() {
        properties = new ToolEngineProperties();
        properties.getCleaner().setMaxBytes(8192);
        cleaner = new ResultCleanerImpl(
                new com.agent.tool.engine.cleaner.AnsiStripper(),
                new com.agent.tool.engine.cleaner.PiiRedactor(),
                properties);
    }

    @Test
    @DisplayName("clean_truncatesOverLong: 10KB → maxBytes=8192 + ...[truncated N bytes]")
    void clean_truncatesOverLong() {
        // 10_000 ASCII chars = 10_000 UTF-8 bytes; exceeds default 8192 budget.
        String longOutput = "x".repeat(10_000);

        CleanedResult result = cleaner.clean(longOutput);

        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getOriginalBytes()).isEqualTo(10_000);
        assertThat(result.getContent()).contains("[truncated");
        assertThat(result.getContent()).contains("bytes]");
        // Final content must fit in the byte budget (suffix included).
        assertThat(result.getContent().getBytes()).hasSizeLessThanOrEqualTo(8192);
    }

    @Test
    @DisplayName("clean_redactsPhone: 13800138000 → 1**********")
    void clean_redactsPhone() {
        String output = "联系 13800138000 请回电";

        CleanedResult result = cleaner.clean(output);

        assertThat(result.getContent()).contains("1**********");
        assertThat(result.getContent()).doesNotContain("13800138000");
        assertThat(result.getRedactionCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("clean_redactsEmail: user@example.com → ***@***.***")
    void clean_redactsEmail() {
        String output = "reply to user@example.com please";

        CleanedResult result = cleaner.clean(output);

        assertThat(result.getContent()).contains("***@***.***");
        assertThat(result.getContent()).doesNotContain("user@example.com");
        assertThat(result.getRedactionCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("clean_redactsApiKey: sk-<48 chars> → sk-****")
    void clean_redactsApiKey() {
        String key = "sk-" + "a".repeat(48);
        String output = "Authorization: Bearer " + key;

        CleanedResult result = cleaner.clean(output);

        assertThat(result.getContent()).contains("sk-****");
        assertThat(result.getContent()).doesNotContain(key);
        assertThat(result.getRedactionCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("clean_redactsIdCard: 110101199001011234 → 18 个星号")
    void clean_redactsIdCard() {
        String output = "id: 110101199001011234 done";

        CleanedResult result = cleaner.clean(output);

        assertThat(result.getContent()).contains("******************");
        assertThat(result.getContent()).doesNotContain("110101199001011234");
        assertThat(result.getRedactionCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("clean_stripsAnsi: hello\\x1B[31mworld\\x1B[0m → helloworld")
    void clean_stripsAnsi() {
        String output = "hello\u001B[31mworld\u001B[0m";

        CleanedResult result = cleaner.clean(output);

        assertThat(result.getContent()).isEqualTo("helloworld");
    }

    @Test
    @DisplayName("clean_trimsTrailingWhitespace: \"hello   \\n\" → \"hello\"")
    void clean_trimsTrailingWhitespace() {
        String output = "hello   \n";

        CleanedResult result = cleaner.clean(output);

        assertThat(result.getContent()).isEqualTo("hello");
    }

    @Test
    @DisplayName("clean_preservesNewlines: multi-line content keeps interior newlines")
    void clean_preservesNewlines() {
        String output = "line1\nline2\nline3";

        CleanedResult result = cleaner.clean(output);

        assertThat(result.getContent()).isEqualTo("line1\nline2\nline3");
    }

    @Test
    @DisplayName("clean_appliesAllInOrder: ANSI + PII + over-long → all applied in sequence")
    void clean_appliesAllInOrder() {
        // Build a payload with ANSI + phone + email + overlong tail.
        String tail = "x".repeat(10_000);
        String output = "\u001B[32mcontact\u001B[0m 13800138000 user@example.com " + tail;

        CleanedResult result = cleaner.clean(output);

        // ANSI stripped: no \u001B remains
        assertThat(result.getContent()).doesNotContain("\u001B");
        // Phone redacted
        assertThat(result.getContent()).contains("1**********");
        assertThat(result.getContent()).doesNotContain("13800138000");
        // Email redacted
        assertThat(result.getContent()).contains("***@***.***");
        assertThat(result.getContent()).doesNotContain("user@example.com");
        // Truncated
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getContent()).contains("[truncated");
        // Redaction count: phone + email (at least 2)
        assertThat(result.getRedactionCount()).isGreaterThanOrEqualTo(2);
        // Final content respects byte budget
        assertThat(result.getContent().getBytes()).hasSizeLessThanOrEqualTo(8192);
    }

    @Test
    @DisplayName("clean_emptyInput_returnsEmpty: null and empty → empty CleanedResult")
    void clean_emptyInput_returnsEmpty() {
        CleanedResult nullResult = cleaner.clean(null);
        CleanedResult emptyResult = cleaner.clean("");

        assertThat(nullResult.getContent()).isEmpty();
        assertThat(nullResult.getOriginalBytes()).isZero();
        assertThat(nullResult.getRedactionCount()).isZero();
        assertThat(emptyResult.getContent()).isEmpty();
        assertThat(emptyResult.getOriginalBytes()).isZero();
    }

    @Test
    @DisplayName("clean_configurableMaxBytes: maxBytes=1024 → truncate at 1024")
    void clean_configurableMaxBytes() {
        properties.getCleaner().setMaxBytes(1024);
        // Re-create cleaner with new config
        ResultCleanerImpl smallCleaner = new ResultCleanerImpl(
                new com.agent.tool.engine.cleaner.AnsiStripper(),
                new com.agent.tool.engine.cleaner.PiiRedactor(),
                properties);

        String longOutput = "y".repeat(5000);

        CleanedResult result = smallCleaner.clean(longOutput);

        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getOriginalBytes()).isEqualTo(5000);
        // Final content must fit within 1024 bytes (suffix included).
        assertThat(result.getContent().getBytes()).hasSizeLessThanOrEqualTo(1024);
        assertThat(result.getContent()).contains("[truncated");
    }

    // ============ Legacy API (clean(String, int)) ============

    @Test
    @DisplayName("cleanLegacy_redactsAndTruncates: ANSI + PII + overlong → string content")
    void cleanLegacy_redactsAndTruncates() {
        String output = "\u001B[31mhello\u001B[0m 13800138000 " + "z".repeat(500);

        String result = cleaner.clean(output, 50);

        assertThat(result).doesNotContain("\u001B");
        assertThat(result).contains("1**********");
        assertThat(result).doesNotContain("13800138000");
        // 50 tokens ≈ 200 chars; output exceeds, so truncation marker expected
        assertThat(result).contains("[truncated");
    }

    @Test
    @DisplayName("cleanLegacy_preservesShortOutput: short clean text → unchanged")
    void cleanLegacy_preservesShortOutput() {
        String output = "short clean output";

        String result = cleaner.clean(output, 2000);

        assertThat(result).isEqualTo(output);
    }
}
