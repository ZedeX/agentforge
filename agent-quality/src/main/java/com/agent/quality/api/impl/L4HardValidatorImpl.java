package com.agent.quality.api.impl;

import com.agent.quality.api.L4HardValidator;
import com.agent.quality.enums.L4ValidationResult;
import com.agent.quality.model.L4ValidationOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * L4-1 规则化硬校验器实现 (doc 11-detail-flow F9.D2, PRD §三(一)2).
 *
 * <p>硬校验项：</p>
 * <ul>
 *   <li>输出非空;</li>
 *   <li>必须包含来源标签 {@code [来源:xxx]};</li>
 *   <li>不得命中黑名单关键词 (绝对 / 100% / 保证);</li>
 *   <li>若输出形似 JSON, 括号须配对平衡 (简易 schema 合法性).</li>
 * </ul>
 * <p>任一违反返回 {@code FORMAT_VIOLATION}, 全部通过返回 {@code PASS}。</p>
 *
 * <p>参考 {@code hallucination-governance} 模块的 L4HardValidatorImpl 规则实现,
 * 此处适配为返回 {@link L4ValidationOutput} 并填写 {@code violationDetail}。</p>
 */
@Component
public class L4HardValidatorImpl implements L4HardValidator {

    private static final Logger log = LoggerFactory.getLogger(L4HardValidatorImpl.class);

    /** 来源标签正则: [来源:任意非空内容]. */
    private static final Pattern SOURCE_TAG_PATTERN = Pattern.compile("\\[来源:[^\\]]+\\]");

    /** 绝对化黑名单关键词: 命中即视为格式违规. */
    private static final Set<String> BLACKLIST = Set.of("绝对", "100%", "保证");

    @Override
    public L4ValidationOutput validate(String output) {
        L4ValidationOutput result = new L4ValidationOutput();
        if (output == null || output.isBlank()) {
            return fail(result, "输出为空");
        }
        if (!SOURCE_TAG_PATTERN.matcher(output).find()) {
            return fail(result, "缺失来源标签 [来源:xxx]");
        }
        if (containsBlacklist(output)) {
            return fail(result, "命中黑名单关键词");
        }
        String jsonCandidate = SOURCE_TAG_PATTERN.matcher(output).replaceAll("").strip();
        if (looksLikeJson(jsonCandidate) && !isBalancedJson(jsonCandidate)) {
            return fail(result, "JSON 括号不配对, schema 校验失败");
        }
        return pass(result);
    }

    private boolean containsBlacklist(String output) {
        for (String keyword : BLACKLIST) {
            if (output.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 简易 JSON 启发式判断: 去除来源标签后首字符为 { 或 [. */
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

    private L4ValidationOutput fail(L4ValidationOutput out, String detail) {
        out.setPassed(false);
        out.setResult(L4ValidationResult.FORMAT_VIOLATION);
        out.setViolationDetail(detail);
        log.warn("L4-1 硬校验失败: {}", detail);
        return out;
    }

    private L4ValidationOutput pass(L4ValidationOutput out) {
        out.setPassed(true);
        out.setResult(L4ValidationResult.PASS);
        out.setViolationDetail(null);
        log.debug("L4-1 硬校验通过");
        return out;
    }
}
