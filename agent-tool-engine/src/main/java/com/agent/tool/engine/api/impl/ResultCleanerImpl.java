package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ResultCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * F8 输出清洗实现 (truncate + summarize + 脱敏)。
 *
 * <p>骨架阶段策略:
 * <ol>
 *   <li>脱敏: 邮箱 / 手机号 / 身份证号替换为掩码</li>
 *   <li>限流: 按 1 token ≈ 4 chars 粗略估算, 超出 maxToken 时截断并追加摘要标注</li>
 * </ol>
 * 生产实现应替换为 tokenizer 精确计算 + LLM 摘要。</p>
 */
@Component
public class ResultCleanerImpl implements ResultCleaner {

    private static final Logger log = LoggerFactory.getLogger(ResultCleanerImpl.class);

    /** 1 token ≈ 4 chars (粗略估算, 骨架阶段)。 */
    private static final int CHARS_PER_TOKEN = 4;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");

    @Override
    public String clean(String rawOutput, int maxToken) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return "";
        }
        if (maxToken <= 0) {
            log.warn("maxToken={} 非法, 直接返回空串", maxToken);
            return "";
        }

        // 1. 脱敏 (按长度从长到短依次替换, 避免短模式吃掉长模式)
        String cleaned = maskSensitive(rawOutput);

        // 2. 限流: 粗略按 4 char/token 估算
        int maxChars = maxToken * CHARS_PER_TOKEN;
        if (cleaned.length() <= maxChars) {
            return cleaned;
        }
        // 3. 摘要化: 截断 + 标注
        int keepChars = Math.max(0, maxChars - 30);
        String truncated = cleaned.substring(0, keepChars);
        String summary = "[...已截断, 原文 " + cleaned.length() + " 字符...]";
        log.debug("输出超限 ({} > {}), 执行截断摘要化", cleaned.length(), maxChars);
        return truncated + summary;
    }

    private String maskSensitive(String text) {
        // 身份证 (18位) 先于手机号 (11位) 替换, 避免手机号正则吃掉身份证前 11 位
        String masked = ID_CARD_PATTERN.matcher(text).replaceAll("******************");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("1**********");
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("***@***.***");
        return masked;
    }
}
