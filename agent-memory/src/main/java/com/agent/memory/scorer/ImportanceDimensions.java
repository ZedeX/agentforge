package com.agent.memory.scorer;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 5 维度重要性评分输入模型（Plan 03 T7 / doc 04-memory §4.2）.
 *
 * <p>各维度取值范围 [0.0, 1.0]：
 * <ul>
 *   <li>{@code emotionIntensity} — 情感强度（权重 0.20）</li>
 *   <li>{@code frequency} — 频率（权重 0.25，accessCount/10 饱和）</li>
 *   <li>{@code novelty} — 新颖度（权重 0.20，1 - 余弦相似度均值）</li>
 *   <li>{@code taskRelevance} — 任务相关性（权重 0.25，关键词命中率）</li>
 *   <li>{@code timeDecay} — 时间衰减（权重 0.10，exp(-Δt/30d)）</li>
 * </ul>
 *
 * <p>权重和 = 0.20 + 0.25 + 0.20 + 0.25 + 0.10 = 1.00。
 */
@Getter
@Setter
@NoArgsConstructor
public class ImportanceDimensions {

    /** 情感强度 [0,1]（权重 0.20）。 */
    private double emotionIntensity = 0.5;
    /** 频率 [0,1]（权重 0.25，accessCount/10 饱和到 1.0）。 */
    private double frequency = 0.5;
    /** 新颖度 [0,1]（权重 0.20，1 - 与同 topic 最近记录的余弦相似度均值）。 */
    private double novelty = 0.5;
    /** 任务相关性 [0,1]（权重 0.25，taskKeywords ∩ record.keywords 命中率）。 */
    private double taskRelevance = 0.5;
    /** 时间衰减 [0,1]（权重 0.10，exp(-Δt/30d)，30 天衰减到 1/e ≈ 0.37）。 */
    private double timeDecay = 0.5;

    public ImportanceDimensions(double emotionIntensity, double frequency,
                                double novelty, double taskRelevance, double timeDecay) {
        this.emotionIntensity = clamp01(emotionIntensity);
        this.frequency = clamp01(frequency);
        this.novelty = clamp01(novelty);
        this.taskRelevance = clamp01(taskRelevance);
        this.timeDecay = clamp01(timeDecay);
    }

    /** 将数值截断到 [0.0, 1.0]。 */
    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
