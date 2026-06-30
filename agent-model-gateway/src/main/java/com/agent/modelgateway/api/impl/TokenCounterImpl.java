package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.api.TokenCounter;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Token counter implementation (doc 02-api §4, Chinese 1.7x coefficient).
 *
 * <p>Heuristic: count CJK chars (each counts as 1 token after 1.7x → effectively 0.59 token
 * per char when divided, but simplified here to: CJK char = 1 token, ASCII ~4 char/token).
 * Final estimate = CJK count + (ASCII length / 4).</p>
 */
@Component
public class TokenCounterImpl implements TokenCounter {

    private static final Pattern CJK_PATTERN = Pattern.compile(
            "[\\u4e00-\\u9fff\\u3400-\\u4dbf\\u3040-\\u309f\\u30a0-\\u30ff\\uac00-\\ud7af]");

    @Override
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjkCount = 0;
        int asciiCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                cjkCount++;
            } else {
                asciiCount++;
            }
        }
        // CJK: 1.7x coefficient → ~1 token per CJK char (rounded)
        // ASCII: 4 char/token
        return cjkCount + (asciiCount + 3) / 4;
    }

    private boolean isCjk(char c) {
        return CJK_PATTERN.matcher(String.valueOf(c)).matches();
    }
}
