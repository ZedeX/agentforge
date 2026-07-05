package com.agent.tool.engine.cleaner;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Strips ANSI escape sequences from tool output (T11).
 *
 * <p>Tool executors (especially sandbox stdout) often emit color codes
 * such as {@code \x1B[31mERROR\x1B[0m}. These sequences break downstream
 * JSON parsing and inflate token usage, so they must be removed before
 * PII redaction and truncation.</p>
 *
 * <p>Pattern: {@code ESC '[' <params> <letter>}, where params is a
 * semicolon-separated list of integers (e.g. {@code 0;31}).
 * Covers SGR (color/style), cursor movement, erase, etc.</p>
 */
@Component
public class AnsiStripper {

    /** CSI sequence: ESC [ <parameter bytes> <intermediate bytes> <final byte>. */
    static final Pattern ANSI_PATTERN =
            Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");

    /**
     * Remove all ANSI escape sequences from the input.
     *
     * @param text raw text, possibly containing ANSI codes (may be null)
     * @return text with ANSI codes removed; null if input is null
     */
    public String strip(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }
}
