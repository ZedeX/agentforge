package com.agent.tool.engine.cleaner;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Redacts personally identifiable information (PII) from tool output (T11).
 *
 * <p>Recognises four PII categories via regex and replaces them with
 * fixed-width masks so downstream consumers cannot recover the original
 * value. Replacement order matters: longer patterns (ID card 18 digits)
 * are applied before shorter ones (phone 11 digits) so the phone regex
 * does not consume the first 11 characters of an ID card.</p>
 *
 * <p>Regexes:
 * <ul>
 *   <li>{@code PHONE}: {@code 1[3-9]\d{9}} (China mobile)</li>
 *   <li>{@code EMAIL}: {@code [\w.+-]+@[\w-]+\.[\w.-]+}</li>
 *   <li>{@code API_KEY}: {@code sk-[A-Za-z0-9]{40,}} (OpenAI-style)</li>
 *   <li>{@code ID_CARD}: {@code \d{17}[\dXx]} (China resident ID)</li>
 * </ul>
 * </p>
 */
@Component
public class PiiRedactor {

    /** China mobile phone number: starts with 1[3-9] followed by 9 digits. */
    public static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /** Email address. */
    public static final Pattern EMAIL_PATTERN =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");

    /** OpenAI-style API key: prefix "sk-" + at least 40 alphanumeric chars. */
    public static final Pattern API_KEY_PATTERN =
            Pattern.compile("sk-[A-Za-z0-9]{40,}");

    /** China resident ID card: 17 digits + 1 digit/X/x. */
    public static final Pattern ID_CARD_PATTERN =
            Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");

    /** Fixed masks (avoid leaking length of original PII). */
    public static final String PHONE_MASK = "1**********";
    public static final String EMAIL_MASK = "***@***.***";
    public static final String API_KEY_MASK = "sk-****";
    public static final String ID_CARD_MASK = "******************";

    /**
     * Redact all PII categories from {@code text}.
     *
     * <p>Order: ID card (18 chars) -> API key (40+ chars) -> phone (11 chars)
     * -> email. Longest patterns first to avoid partial overlaps.</p>
     *
     * @param text raw text, possibly containing PII (may be null)
     * @return redacted text; null if input is null
     */
    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = ID_CARD_PATTERN.matcher(text).replaceAll(ID_CARD_MASK);
        masked = API_KEY_PATTERN.matcher(masked).replaceAll(API_KEY_MASK);
        masked = PHONE_PATTERN.matcher(masked).replaceAll(PHONE_MASK);
        masked = EMAIL_PATTERN.matcher(masked).replaceAll(EMAIL_MASK);
        return masked;
    }
}
