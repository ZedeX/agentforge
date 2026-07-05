package com.agent.tool.engine.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * PII (Personally Identifiable Information) detector for risk classification boost.
 *
 * <p>Scans input JSON / params text for sensitive patterns. When detected,
 * the risk classifier must promote the assessment to at least R3 and force
 * approval (doc 05-tool-engine §4.2 "R3 + 涉及 PII → 强制审批").</p>
 *
 * <p>Supported PII patterns (doc 05 §9.2):</p>
 * <ul>
 *   <li>Phone: {@code 1[3-9]\d{9}} (China mainland mobile)</li>
 *   <li>Email: standard RFC-like pattern</li>
 *   <li>ID card: {@code \d{17}[\dXx]} (China 18-digit resident ID)</li>
 *   <li>API key: {@code sk-[A-Za-z0-9]{40,}} (OpenAI-style secret)</li>
 * </ul>
 */
@Component
public class PiiDetector {

    private static final Logger log = LoggerFactory.getLogger(PiiDetector.class);

    // 中国大陆手机号: 1[3-9] + 9 位数字
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("1[3-9]\\d{9}");

    // 邮箱: 简化 RFC 模式
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");

    // 身份证: 18 位 (前 17 位数字 + 末位数字或 X)
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("\\d{17}[\\dXx]");

    // API key: sk- 开头 + 至少 40 个字母数字 (OpenAI 风格)
    private static final Pattern API_KEY_PATTERN =
            Pattern.compile("sk-[A-Za-z0-9]{40,}");

    /**
     * Detect whether the input text contains any PII pattern.
     *
     * @param text input JSON / params text, may be null
     * @return true if any PII pattern matches
     */
    public boolean containsPii(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        boolean hit = PHONE_PATTERN.matcher(text).find()
                || EMAIL_PATTERN.matcher(text).find()
                || ID_CARD_PATTERN.matcher(text).find()
                || API_KEY_PATTERN.matcher(text).find();
        if (hit) {
            log.debug("PII detected in input (length={})", text.length());
        }
        return hit;
    }

    /**
     * Detect PII and return the first matched category for audit logging.
     *
     * <p>Check order is from most-specific to least-specific to avoid
     * ambiguous substring matches (e.g. an 18-digit ID card may contain
     * an 11-digit phone substring). Order: API_KEY (sk-prefix) → ID_CARD
     * (18 digits) → EMAIL (contains @) → PHONE (11 digits).</p>
     *
     * @param text input text
     * @return matched category name, or null if no PII found
     */
    public String detectCategory(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (API_KEY_PATTERN.matcher(text).find()) {
            return "API_KEY";
        }
        if (ID_CARD_PATTERN.matcher(text).find()) {
            return "ID_CARD";
        }
        if (EMAIL_PATTERN.matcher(text).find()) {
            return "EMAIL";
        }
        if (PHONE_PATTERN.matcher(text).find()) {
            return "PHONE";
        }
        return null;
    }
}
