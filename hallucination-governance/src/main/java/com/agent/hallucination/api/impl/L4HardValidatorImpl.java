package com.agent.hallucination.api.impl;

import com.agent.hallucination.api.L4HardValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Layer 4 L4-1 硬校验器实现 (F10 L4: 规则式硬校验兜底)。
 *
 * <p>规则 (doc 11 F9.D2)：</p>
 * <ul>
 *   <li>输出必须包含来源标签 {@code [来源:xxx]}；</li>
 *   <li>若输出形似 JSON (去除来源标签后以 {@code {} 或 {@code [} 开头), 必须括号配对平衡 (简易 schema 合法性校验)；</li>
 *   <li>不得命中黑名单关键词 (绝对 / 100% / 保证)。</li>
 * </ul>
 * <p>三条规则全部通过返回 {@code true}, 任一违反返回 {@code false}。</p>
 */
@Component
public class L4HardValidatorImpl implements L4HardValidator {

    private static final Logger log = LoggerFactory.getLogger(L4HardValidatorImpl.class);

    /** 来源标签正则: [来源:任意非空内容]。 */
    private static final Pattern SOURCE_TAG_PATTERN = Pattern.compile("\\[来源:[^\\]]+\\]");

    /** 绝对化黑名单关键词: 命中即视为幻觉风险。 */
    private static final Set<String> BLACKLIST = Set.of("绝对", "100%", "保证");

    @Override
    public boolean validate(String output) {
        if (output == null || output.isBlank()) {
            log.warn("L4 硬校验: 输出为空, 校验失败");
            return false;
        }
        if (!SOURCE_TAG_PATTERN.matcher(output).find()) {
            log.warn("L4 硬校验: 缺失来源标签 [来源:xxx], 校验失败");
            return false;
        }
        if (containsBlacklist(output)) {
            log.warn("L4 硬校验: 命中黑名单关键词, 校验失败");
            return false;
        }
        // 去除来源标签后再做 JSON 括号配对校验, 避免来源标签中的 [] 干扰判断
        String jsonCandidate = SOURCE_TAG_PATTERN.matcher(output).replaceAll("").strip();
        if (looksLikeJson(jsonCandidate) && !isBalancedJson(jsonCandidate)) {
            log.warn("L4 硬校验: JSON 括号不配对, schema 校验失败");
            return false;
        }
        log.debug("L4 硬校验通过");
        return true;
    }

    private boolean containsBlacklist(String output) {
        for (String keyword : BLACKLIST) {
            if (output.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 简易 JSON 启发式判断: 去除来源标签后首字符为 { 或 [。 */
    private boolean looksLikeJson(String candidate) {
        return candidate.startsWith("{") || candidate.startsWith("[");
    }

    /**
     * 简易 JSON 合法性: 统计 { } [ ] 是否配对平衡, 闭括号先于开括号出现则立即判失败。
     * <p>忽略字符串内转义, 仅供骨架阶段使用。</p>
     */
    private boolean isBalancedJson(String candidate) {
        int brace = 0;
        int bracket = 0;
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            switch (c) {
                case '{': brace++; break;
                case '}': brace--; break;
                case '[': bracket++; break;
                case ']': bracket--; break;
                default: break;
            }
            if (brace < 0 || bracket < 0) {
                return false;
            }
        }
        return brace == 0 && bracket == 0;
    }
}
