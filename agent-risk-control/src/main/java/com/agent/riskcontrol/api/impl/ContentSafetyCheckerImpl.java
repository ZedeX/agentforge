package com.agent.riskcontrol.api.impl;

import com.agent.riskcontrol.api.ContentSafetyChecker;
import com.agent.riskcontrol.config.RiskControlProperties;
import com.agent.riskcontrol.entity.ContentViolationEntity;
import com.agent.riskcontrol.model.CheckContentRequest;
import com.agent.riskcontrol.model.CheckContentResponse;
import com.agent.riskcontrol.model.ContentViolation;
import com.agent.riskcontrol.repository.ContentViolationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Content safety checker implementation.
 *
 * <p>Detects:
 * <ul>
 *   <li>Sensitive words (configurable list)</li>
 *   <li>PII: phone numbers, email addresses, ID card numbers (China)</li>
 *   <li>Injection patterns: SQL injection, prompt injection</li>
 * </ul>
 *
 * <p>PII regex patterns reused from agent-tool-engine PiiRedactor (same field conventions).
 */
@Slf4j
@Component
public class ContentSafetyCheckerImpl implements ContentSafetyChecker {

    /** China mobile phone number: starts with 1[3-9] followed by 9 digits. */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /** Email address. */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");

    /** China resident ID card: 17 digits + 1 digit/X/x. */
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");

    /** SQL injection patterns. */
    private static final Pattern SQL_INJECTION_PATTERN =
            Pattern.compile("(?i)(\\b(union\\s+select|select\\s+.+\\s+from|insert\\s+into|delete\\s+from|drop\\s+table|alter\\s+table|exec\\s*\\(|execute\\s*\\(|--|;\\s*--|1\\s*=\\s*1|'\\s*or\\s+'|\"\\s*or\\s+\")\\b)");

    /** Prompt injection patterns. */
    private static final Pattern PROMPT_INJECTION_PATTERN =
            Pattern.compile("(?i)(ignore\\s+(previous|above|all)\\s*instructions|disregard\\s+(previous|above)\\s*instructions|you\\s+are\\s+now|new\\s+instructions|system\\s*:\\s*|\\[INST\\]|<\\|im_start\\|>|jailbreak|bypass\\s+(safety|filter|restriction))");

    private static final String PHONE_MASK = "1**********";
    private static final String EMAIL_MASK = "***@***.***";
    private static final String ID_CARD_MASK = "******************";

    private final RiskControlProperties properties;
    private final ContentViolationRepository violationRepository;

    public ContentSafetyCheckerImpl(RiskControlProperties properties,
                                    ContentViolationRepository violationRepository) {
        this.properties = properties;
        this.violationRepository = violationRepository;
    }

    @Override
    public CheckContentResponse check(CheckContentRequest request) {
        String content = request.getContent();
        if (content == null || content.isEmpty()) {
            return new CheckContentResponse(true, new ArrayList<>(), "");
        }

        List<ContentViolation> violations = new ArrayList<>();
        List<String> categories = request.getCheckCategories();

        // 1. Sensitive word detection
        if (shouldCheck(categories, "sensitive_word")) {
            checkSensitiveWords(content, violations);
        }

        // 2. PII detection
        if (properties.getContent().isEnablePiiDetection() && shouldCheck(categories, "pii")) {
            checkPii(content, violations);
        }

        // 3. Injection detection
        if (properties.getContent().isEnableInjectionDetection() && shouldCheck(categories, "injection")) {
            checkInjection(content, violations);
        }

        // Sanitize content by masking PII
        String sanitized = sanitizeContent(content);

        // Log violations
        for (ContentViolation v : violations) {
            logViolation(v, content);
        }

        boolean safe = violations.isEmpty();
        return new CheckContentResponse(safe, violations, sanitized);
    }

    private boolean shouldCheck(List<String> categories, String category) {
        return categories == null || categories.isEmpty() || categories.contains(category);
    }

    private void checkSensitiveWords(String content, List<ContentViolation> violations) {
        List<String> sensitiveWords = properties.getContent().getSensitiveWords();
        for (String word : sensitiveWords) {
            int idx = content.indexOf(word);
            if (idx >= 0) {
                violations.add(new ContentViolation(
                        "sensitive_word", "medium",
                        "Sensitive word detected: " + word, idx));
            }
        }
    }

    private void checkPii(String content, List<ContentViolation> violations) {
        var phoneMatcher = PHONE_PATTERN.matcher(content);
        while (phoneMatcher.find()) {
            violations.add(new ContentViolation(
                    "pii", "high",
                    "Phone number detected", phoneMatcher.start()));
        }

        var emailMatcher = EMAIL_PATTERN.matcher(content);
        while (emailMatcher.find()) {
            violations.add(new ContentViolation(
                    "pii", "medium",
                    "Email address detected", emailMatcher.start()));
        }

        var idCardMatcher = ID_CARD_PATTERN.matcher(content);
        while (idCardMatcher.find()) {
            violations.add(new ContentViolation(
                    "pii", "critical",
                    "ID card number detected", idCardMatcher.start()));
        }
    }

    private void checkInjection(String content, List<ContentViolation> violations) {
        var sqlMatcher = SQL_INJECTION_PATTERN.matcher(content);
        while (sqlMatcher.find()) {
            violations.add(new ContentViolation(
                    "injection", "critical",
                    "SQL injection pattern detected", sqlMatcher.start()));
        }

        var promptMatcher = PROMPT_INJECTION_PATTERN.matcher(content);
        while (promptMatcher.find()) {
            violations.add(new ContentViolation(
                    "injection", "high",
                    "Prompt injection pattern detected", promptMatcher.start()));
        }
    }

    private String sanitizeContent(String content) {
        if (!properties.getContent().isEnablePiiDetection()) {
            return content;
        }
        String masked = ID_CARD_PATTERN.matcher(content).replaceAll(ID_CARD_MASK);
        masked = PHONE_PATTERN.matcher(masked).replaceAll(PHONE_MASK);
        masked = EMAIL_PATTERN.matcher(masked).replaceAll(EMAIL_MASK);
        return masked;
    }

    private void logViolation(ContentViolation violation, String content) {
        try {
            ContentViolationEntity entity = new ContentViolationEntity();
            entity.setViolationId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            entity.setContentType("text");
            entity.setCategory(violation.getCategory());
            entity.setSeverity(violation.getSeverity());
            // Truncate content for storage (avoid storing full sensitive content)
            entity.setContent(content.length() > 200 ? content.substring(0, 200) + "..." : content);
            entity.setDetail(violation.getDetail());
            entity.setDetectedAt(Instant.now());
            violationRepository.save(entity);
        } catch (Exception e) {
            // Intentionally swallowed: violation logging is best-effort and must not
            // block the main content-safety check flow. Outbox compensation is not
            // needed because a missed violation log is acceptable (non-critical side-effect).
            log.warn("Failed to log content violation: {}", e.getMessage(), e);
        }
    }
}
