package com.agent.quality.api.impl;

import com.agent.quality.api.L4AuditValidator;
import com.agent.quality.enums.L4ValidationResult;
import com.agent.quality.model.L4ValidationOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * L4-3 强模型终审校验器实现 (doc 11-detail-flow F9.D4, PRD §三(一)2).
 *
 * <p>骨架阶段策略: 真实生产应调用强模型对输出做四维度评分（事实性 / 完整性 / 一致性 / 安全性），
 * 此处以规则化启发式近似 overall 评分：</p>
 * <ul>
 *   <li>输出非空: 基础分 0.3;</li>
 *   <li>含来源标签 [来源:xxx]: +0.3 (事实性 / 可溯源性);</li>
 *   <li>未命中黑名单 (绝对 / 100% / 保证): +0.2 (安全性);</li>
 *   <li>输出长度 ≥ 50: +0.2 (完整性).</li>
 * </ul>
 * <p>当 overall ≥ overallScoreThreshold 通过; 否则返回 {@code AUDIT_REJECTED}, 转人工.</p>
 */
@Component
public class L4AuditValidatorImpl implements L4AuditValidator {

    private static final Logger log = LoggerFactory.getLogger(L4AuditValidatorImpl.class);

    /** 默认 overall 评分阈值, 见 PRD §三(一)2 / doc 11 F9.D4. */
    public static final double DEFAULT_THRESHOLD = 0.7;

    /** 来源标签正则: [来源:任意非空内容]. */
    private static final Pattern SOURCE_TAG_PATTERN = Pattern.compile("\\[来源:[^\\]]+\\]");

    /** 绝对化黑名单关键词: 命中即视为安全性风险, 扣分. */
    private static final Set<String> BLACKLIST = Set.of("绝对", "100%", "保证");

    /** 完整性加分所需的最小输出长度. */
    private static final int COMPLETENESS_MIN_LENGTH = 50;

    @Override
    public L4ValidationOutput validate(String output, double overallScoreThreshold) {
        L4ValidationOutput result = new L4ValidationOutput();
        if (output == null || output.isBlank()) {
            return reject(result, 0.0, overallScoreThreshold, "模型输出为空");
        }
        double score = computeOverallScore(output);
        result.setOverallScore(score);
        if (score >= overallScoreThreshold) {
            result.setPassed(true);
            result.setResult(L4ValidationResult.PASS);
            result.setViolationDetail(null);
            log.debug("L4-3 终审通过: overall={}, threshold={}", score, overallScoreThreshold);
            return result;
        }
        return reject(result, score, overallScoreThreshold,
                "overall=" + String.format(Locale.ROOT, "%.4f", score) + " < 阈值 " + overallScoreThreshold);
    }

    /**
     * 规则化启发式 overall 评分:
     * 基础 0.3 + 含来源标签 +0.3 + 未命中黑名单 +0.2 + 长度 ≥ 50 +0.2, 满分 1.0.
     */
    private double computeOverallScore(String output) {
        double score = 0.3;
        if (SOURCE_TAG_PATTERN.matcher(output).find()) {
            score += 0.3;
        }
        if (!containsBlacklist(output)) {
            score += 0.2;
        }
        if (output.length() >= COMPLETENESS_MIN_LENGTH) {
            score += 0.2;
        }
        return score;
    }

    private boolean containsBlacklist(String output) {
        for (String keyword : BLACKLIST) {
            if (output.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private L4ValidationOutput reject(L4ValidationOutput out, double score, double threshold, String detail) {
        out.setPassed(false);
        out.setResult(L4ValidationResult.AUDIT_REJECTED);
        out.setOverallScore(score);
        out.setViolationDetail(detail);
        log.warn("L4-3 终审驳回: {} (threshold={})", detail, threshold);
        return out;
    }
}
