package com.agent.memory.api.impl;

import com.agent.memory.api.ImportanceScorer;
import com.agent.memory.config.MemoryProperties;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.scorer.ImportanceDimensions;
import com.agent.memory.scorer.ImportanceResult;
import com.agent.memory.scorer.ScoringContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

/**
 * 重要性评分实现（Plan 03 T7 / doc 04-memory §4.2 + §4.3）.
 *
 * <p>5 维度加权评分：
 * <pre>
 *   score = 0.20 * emotionIntensity
 *         + 0.25 * frequency
 *         + 0.20 * novelty
 *         + 0.25 * taskRelevance
 *         + 0.10 * timeDecay
 * </pre>
 *
 * <p>权重和 = 0.20 + 0.25 + 0.20 + 0.25 + 0.10 = 1.00，最终 score ∈ [0,1]。
 *
 * <p>等级阈值（doc 04 §4.3）：score≥0.7 → HIGH / 0.4≤score&lt;0.7 → MEDIUM / score&lt;0.4 → LOW。
 *
 * <p>两条路径共存：
 * <ul>
 *   <li>{@code score(MemoryRecord, ScoringContext)} — Plan 03 T7 新接口，5 维度评分返回
 *       {@link ImportanceResult}（含 score + level + dimensions 明细）</li>
 *   <li>{@code score(int, double, double)} — F12.D3 旧接口，3 维度加权（relevance 0.4 +
 *       recency 0.3 + freq 0.3）返回 double，向后兼容</li>
 * </ul>
 *
 * @see ImportanceScorer
 * @see ImportanceDimensions
 * @see ScoringContext
 * @see ImportanceResult
 */
@Component
public class ImportanceScorerImpl implements ImportanceScorer {

    private static final Logger log = LoggerFactory.getLogger(ImportanceScorerImpl.class);

    // ============ 5 维度权重（doc 04 §4.2，权重和=1.0） ============
    /** 情感强度权重。 */
    static final double W_EMOTION = 0.20;
    /** 频率权重。 */
    static final double W_FREQUENCY = 0.25;
    /** 新颖度权重。 */
    static final double W_NOVELTY = 0.20;
    /** 任务相关性权重。 */
    static final double W_TASK_RELEVANCE = 0.25;
    /** 时间衰减权重。 */
    static final double W_TIME_DECAY = 0.10;

    /** 旧接口 relevance 权重。 */
    static final double LEGACY_W_RELEVANCE = 0.4;
    /** 旧接口 recency 权重。 */
    static final double LEGACY_W_RECENCY = 0.3;
    /** 旧接口 freq 权重。 */
    static final double LEGACY_W_FREQ = 0.3;
    /** accessCount 达到此值即视为 freq 满分。 */
    static final double FREQ_SATURATION = 10.0;

    /** 时间衰减常数（30 天衰减到 1/e ≈ 0.37，doc 04 §4.2）。 */
    static final double TIME_DECAY_DAYS = 30.0;

    private final MemoryProperties properties;

    public ImportanceScorerImpl() {
        this(new MemoryProperties());
    }

    public ImportanceScorerImpl(MemoryProperties properties) {
        this.properties = properties;
    }

    // ============ Plan 03 T7 新接口：5 维度加权评分 ============

    @Override
    public ImportanceResult score(MemoryRecord record, ScoringContext context) {
        if (record == null) {
            log.warn("重要性评分失败：MemoryRecord 为 null");
            return new ImportanceResult(0.0, "LOW", new ImportanceDimensions());
        }
        if (context == null) {
            context = ScoringContext.empty();
        }

        // 1. emotionIntensity：从 context 预计算值或默认 0.5
        double emotion = context.getEmotionIntensity() != null
                ? clamp01(context.getEmotionIntensity()) : 0.5;

        // 2. frequency：accessCount/10 饱和到 1.0
        int accessCount = record.getRecallCount();
        double frequency = Math.min(accessCount / FREQ_SATURATION, 1.0);

        // 3. novelty：1 - 与 referenceVectors 的余弦相似度均值
        double novelty = computeNovelty(record, context);

        // 4. taskRelevance：taskKeywords ∩ record.keywords 命中率
        double taskRelevance = computeTaskRelevance(record, context);

        // 5. timeDecay：exp(-Δt/30d)
        double timeDecay = computeTimeDecay(record, context);

        ImportanceDimensions dimensions = new ImportanceDimensions(
                emotion, frequency, novelty, taskRelevance, timeDecay);

        double score = clamp01(
                W_EMOTION * dimensions.getEmotionIntensity()
                        + W_FREQUENCY * dimensions.getFrequency()
                        + W_NOVELTY * dimensions.getNovelty()
                        + W_TASK_RELEVANCE * dimensions.getTaskRelevance()
                        + W_TIME_DECAY * dimensions.getTimeDecay()
        );

        String level = ImportanceResult.classify(score);

        log.info("重要性评分 memoryId={} score={} level={} dims=[emo={} freq={} nov={} rel={} dec={}]",
                record.getMemoryId(), score, level,
                dimensions.getEmotionIntensity(), dimensions.getFrequency(),
                dimensions.getNovelty(), dimensions.getTaskRelevance(),
                dimensions.getTimeDecay());

        return new ImportanceResult(score, level, dimensions);
    }

    // ============ F12.D3 旧接口：3 维度加权（向后兼容） ============

    @Override
    public double score(int accessCount, double recency, double relevance) {
        double recencyClamped = clamp01(recency);
        double relevanceClamped = clamp01(relevance);
        double freqNorm = accessCount <= 0 ? 0.0
                : Math.min(accessCount / FREQ_SATURATION, 1.0);

        double raw = LEGACY_W_RELEVANCE * relevanceClamped
                + LEGACY_W_RECENCY * recencyClamped
                + LEGACY_W_FREQ * freqNorm;
        double result = clamp01(raw);
        log.debug("重要性评分（旧接口） accessCount={} recency={} relevance={} -> {}",
                accessCount, recency, relevance, result);
        return result;
    }

    // ============ 私有工具方法 ============

    /** 计算新颖度：1 - 与 referenceVectors 的余弦相似度均值（无参考时默认 0.5）。 */
    private double computeNovelty(MemoryRecord record, ScoringContext context) {
        float[][] references = context.getReferenceVectors();
        if (references == null || references.length == 0) {
            return 0.5;  // 无参考向量，新颖度退化为默认值
        }
        // 注意：record 自身的向量需要外部传入或从 vectorId 查询
        // T7 阶段简化：novelty 直接用默认值 0.5，等 T5/T6 完成后再接入真实向量
        // 这里保留接口扩展点，调用方可通过 ScoringContext.referenceVectors 传入
        return 0.5;
    }

    /** 计算任务相关性：taskKeywords ∩ record.keywords 命中率。 */
    private double computeTaskRelevance(MemoryRecord record, ScoringContext context) {
        Set<String> taskKeywords = context.getTaskKeywords();
        if (taskKeywords == null || taskKeywords.isEmpty()) {
            return 0.5;  // 无任务关键词，相关性退化为默认值
        }

        // 从 record 提取关键词（keywords 字段是 JSON 数组字符串，简化处理）
        String keywordsStr = record.getKeywords();
        if (keywordsStr == null || keywordsStr.isBlank()) {
            return 0.0;  // record 无关键词，相关性为 0
        }

        // 简化：将 keywordsStr 按逗号/分号/空格分割，与 taskKeywords 求交集
        Set<String> recordKeywords = parseKeywords(keywordsStr);
        if (recordKeywords.isEmpty()) {
            return 0.0;
        }

        long hits = taskKeywords.stream()
                .filter(recordKeywords::contains)
                .count();
        return clamp01((double) hits / taskKeywords.size());
    }

    /** 计算时间衰减：exp(-Δt/30d)，Δt = now - createdAt（天数）。 */
    private double computeTimeDecay(MemoryRecord record, ScoringContext context) {
        Instant createdAt = record.getCreatedAt();
        if (createdAt == null) {
            return 0.5;  // 无创建时间，退化为默认值
        }
        Instant now = context.getNow() != null ? context.getNow() : Instant.now();
        long daysElapsed = Duration.between(createdAt, now).toDays();
        if (daysElapsed < 0) {
            return 1.0;  // 未来时间，衰减为 0（即 timeDecay=1.0）
        }
        return clamp01(Math.exp(-daysElapsed / TIME_DECAY_DAYS));
    }

    /** 解析 keywords 字符串（支持 JSON 数组、逗号、分号、空格分隔）。 */
    private Set<String> parseKeywords(String keywordsStr) {
        // 简化：去掉方括号和引号，按逗号/分号/空格分割
        String cleaned = keywordsStr.replaceAll("[\\[\\]\"]", "");
        if (cleaned.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(cleaned.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 将数值截断到 [0.0, 1.0]。 */
    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
