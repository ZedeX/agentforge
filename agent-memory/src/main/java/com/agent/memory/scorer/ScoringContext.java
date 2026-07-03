package com.agent.memory.scorer;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;

/**
 * 评分上下文（Plan 03 T7）.
 *
 * <p>提供 5 维度评分所需的外部信息：
 * <ul>
 *   <li>{@code taskKeywords} — 当前任务关键词集合（用于 taskRelevance 维度计算）</li>
 *   <li>{@code referenceVectors} — 同 topic 最近 N 条记录的向量（用于 novelty 维度计算），
 *       为 null 或空时 novelty 退化为默认值 0.5</li>
 *   <li>{@code now} — 评分基准时间（用于 timeDecay 维度计算），为 null 时用系统当前时间</li>
 *   <li>{@code emotionIntensity} — 预计算的情感强度（可选，从 metadata 提取），
 *       为 null 时 scorer 使用默认值 0.5</li>
 * </ul>
 *
 * <p>调用方（如 LongTermMemoryWriter）负责用 EmbeddingClient 生成 referenceVectors
 * 后传入，ImportanceScorerImpl 不直接依赖 EmbeddingClient（保持自包含可测试）。
 */
@Getter
@Setter
@NoArgsConstructor
public class ScoringContext {

    /** 当前任务关键词集合（用于 taskRelevance 维度）。 */
    private Set<String> taskKeywords;
    /** 同 topic 最近 N 条记录的向量（用于 novelty 维度），null/空时退化为默认值。 */
    private float[][] referenceVectors;
    /** 评分基准时间，null 时用 Instant.now()。 */
    private Instant now;
    /** 预计算的情感强度（可选），null 时 scorer 用默认值 0.5。 */
    private Double emotionIntensity;

    public ScoringContext(Set<String> taskKeywords, float[][] referenceVectors,
                          Instant now, Double emotionIntensity) {
        this.taskKeywords = taskKeywords;
        this.referenceVectors = referenceVectors;
        this.now = now;
        this.emotionIntensity = emotionIntensity;
    }

    /** 创建空上下文（所有维度用默认值）。 */
    public static ScoringContext empty() {
        return new ScoringContext();
    }
}
