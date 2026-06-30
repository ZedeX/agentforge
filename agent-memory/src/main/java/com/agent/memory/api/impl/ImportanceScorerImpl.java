package com.agent.memory.api.impl;

import com.agent.memory.api.ImportanceScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 记忆重要性评分实现（F12.D3: freq x recency x relevance）。
 *
 * <p>按 freq × recency × relevance 加权计算重要性分数，公式：
 * <pre>
 *   freqNorm = min(accessCount / 10.0, 1.0)        // accessCount 满 10 次即视为 freq 满分
 *   score    = 0.4 * relevance + 0.3 * recency + 0.3 * freqNorm
 * </pre>
 * 最终结果 clamp 到 [0.0, 1.0]。</p>
 *
 * <p>权重设计：relevance（相关性）权重最高 0.4，recency（时近性）与
 * freq（访问频次）各占 0.3。负的 accessCount 视为 0。</p>
 *
 * @see ImportanceScorer
 */
@Component
public class ImportanceScorerImpl implements ImportanceScorer {

    private static final Logger log = LoggerFactory.getLogger(ImportanceScorerImpl.class);

    /** relevance 权重。 */
    static final double WEIGHT_RELEVANCE = 0.4;
    /** recency 权重。 */
    static final double WEIGHT_RECENCY = 0.3;
    /** freq 权重。 */
    static final double WEIGHT_FREQ = 0.3;
    /** accessCount 达到此值即视为 freq 满分。 */
    static final double FREQ_SATURATION = 10.0;

    @Override
    public double score(int accessCount, double recency, double relevance) {
        double recencyClamped = clamp01(recency);
        double relevanceClamped = clamp01(relevance);
        double freqNorm = accessCount <= 0 ? 0.0
                : Math.min(accessCount / FREQ_SATURATION, 1.0);

        double raw = WEIGHT_RELEVANCE * relevanceClamped
                + WEIGHT_RECENCY * recencyClamped
                + WEIGHT_FREQ * freqNorm;
        double result = clamp01(raw);
        log.debug("重要性评分 accessCount={} recency={} relevance={} -> {}",
                accessCount, recency, relevance, result);
        return result;
    }

    /** 将数值截断到 [0.0, 1.0]。 */
    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}