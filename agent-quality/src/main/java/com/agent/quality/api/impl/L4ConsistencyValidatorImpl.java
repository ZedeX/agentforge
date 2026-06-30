package com.agent.quality.api.impl;

import com.agent.quality.api.L4ConsistencyValidator;
import com.agent.quality.enums.L4ValidationResult;
import com.agent.quality.model.L4ValidationOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * L4-2 事实一致性校验器实现 (doc 11-detail-flow F9.D3, PRD §三(一)2).
 *
 * <p>骨架阶段策略：使用基于 token 频次的余弦相似度计算 output 与 referenceSource 的事实一致性,
 * 当 {@code cosineSim ≥ 0.75} 通过; 否则返回 {@code FACT_INCONSISTENCY} 触发 Reflexion。</p>
 *
 * <p>注意: 真实生产实现应使用向量模型 (如 bge / e5) 编码后做 cosine, 此处仅以
 * 字符级 n-gram 频次余弦近似用于骨架联调与单测。</p>
 */
@Component
public class L4ConsistencyValidatorImpl implements L4ConsistencyValidator {

    private static final Logger log = LoggerFactory.getLogger(L4ConsistencyValidatorImpl.class);

    /** F9.D3 事实一致性阈值: cosine_sim ≥ 0.75 通过. */
    public static final double COSINE_THRESHOLD = 0.75;

    /** 中文字符级 n-gram 长度 (2-gram 简化处理, 兼容中英文混合). */
    private static final int NGRAM_SIZE = 2;

    @Override
    public L4ValidationOutput validate(String output, String referenceSource) {
        L4ValidationOutput result = new L4ValidationOutput();
        if (output == null || output.isBlank()) {
            return fail(result, 0.0, "模型输出为空");
        }
        if (referenceSource == null || referenceSource.isBlank()) {
            return fail(result, 0.0, "参考源为空");
        }
        double cosineSim = cosineSimilarity(output, referenceSource);
        result.setCosineSim(cosineSim);
        if (cosineSim >= COSINE_THRESHOLD) {
            result.setPassed(true);
            result.setResult(L4ValidationResult.PASS);
            result.setViolationDetail(null);
            log.debug("L4-2 一致性校验通过: cosineSim={}", cosineSim);
            return result;
        }
        return fail(result, cosineSim, "cosine_sim=" + String.format(Locale.ROOT, "%.4f", cosineSim) + " < 阈值 " + COSINE_THRESHOLD);
    }

    /**
     * 计算 output 与 referenceSource 的字符级 2-gram 频次余弦相似度.
     * <p>返回 [0, 1] 区间 double, 0 表示无交集, 1 表示完全相同。</p>
     */
    private double cosineSimilarity(String output, String referenceSource) {
        Map<String, Integer> vecA = buildNgramVector(output);
        Map<String, Integer> vecB = buildNgramVector(referenceSource);
        if (vecA.isEmpty() || vecB.isEmpty()) {
            return 0.0;
        }
        double dot = 0.0;
        for (Map.Entry<String, Integer> e : vecA.entrySet()) {
            Integer b = vecB.get(e.getKey());
            if (b != null) {
                dot += (double) e.getValue() * b;
            }
        }
        double normA = norm(vecA);
        double normB = norm(vecB);
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (normA * normB);
    }

    private Map<String, Integer> buildNgramVector(String text) {
        String normalized = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        Map<String, Integer> vector = new HashMap<>();
        if (normalized.length() < NGRAM_SIZE) {
            if (!normalized.isEmpty()) {
                vector.put(normalized, 1);
            }
            return vector;
        }
        for (int i = 0; i + NGRAM_SIZE <= normalized.length(); i++) {
            String ngram = normalized.substring(i, i + NGRAM_SIZE);
            vector.merge(ngram, 1, Integer::sum);
        }
        return vector;
    }

    private double norm(Map<String, Integer> vec) {
        double sum = 0.0;
        for (int v : vec.values()) {
            sum += (double) v * v;
        }
        return Math.sqrt(sum);
    }

    private L4ValidationOutput fail(L4ValidationOutput out, double cosineSim, String detail) {
        out.setPassed(false);
        out.setResult(L4ValidationResult.FACT_INCONSISTENCY);
        out.setCosineSim(cosineSim);
        out.setViolationDetail(detail);
        log.warn("L4-2 一致性校验失败: {}", detail);
        return out;
    }
}
