package com.agent.memory.api.impl;

import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.scorer.ImportanceDimensions;
import com.agent.memory.scorer.ImportanceResult;
import com.agent.memory.scorer.ScoringContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * ImportanceScorerImpl 单元测试（Plan 03 T7）.
 *
 * <p>覆盖两条路径：
 * <ul>
 *   <li>新接口 {@code score(MemoryRecord, ScoringContext)} — Plan 03 T7 设计，
 *       5 维度加权评分（emotionIntensity 0.20 + frequency 0.25 + novelty 0.20 +
 *       taskRelevance 0.25 + timeDecay 0.10）返回 ImportanceResult</li>
 *   <li>旧接口 {@code score(int, double, double)} — F12.D3 骨架向后兼容，
 *       3 维度加权（relevance 0.4 + recency 0.3 + freq 0.3）返回 double</li>
 * </ul>
 */
class ImportanceScorerImplTest {

    private ImportanceScorerImpl createScorer() {
        return new ImportanceScorerImpl();
    }

    private MemoryRecord buildRecord(String id, int recallCount, Instant createdAt, String keywords) {
        MemoryRecord r = new MemoryRecord(id, MemoryType.EPISODIC, "内容 " + id);
        r.setRecallCount(recallCount);
        r.setCreatedAt(createdAt);
        if (keywords != null) {
            r.setKeywords(keywords);
        }
        return r;
    }

    // ============ 5 维度评分核心测试（Plan 03 T7 Red） ============

    @Test
    @DisplayName("score(MemoryRecord, ScoringContext) 5 维度全 1.0 应返回 HIGH 等级")
    void should_ReturnHigh_When_AllDimensionsHigh() {
        ImportanceScorerImpl scorer = createScorer();
        // recallCount=10 -> frequency=1.0
        MemoryRecord record = buildRecord("mem_high", 10, Instant.now(), "订单,支付,查询");
        // emotion=1.0, taskKeywords 命中全部 -> taskRelevance=1.0, createdAt=now -> timeDecay=1.0
        ScoringContext context = new ScoringContext(
                Set.of("订单", "支付", "查询"), null, Instant.now(), 1.0);

        ImportanceResult result = scorer.score(record, context);

        assertThat(result.getScore()).isGreaterThanOrEqualTo(0.7);
        assertThat(result.getLevel()).isEqualTo("HIGH");
        // 各维度应为 1.0（novelty 默认 0.5 除外，因无 referenceVectors）
        assertThat(result.getDimensions().getEmotionIntensity()).isEqualTo(1.0);
        assertThat(result.getDimensions().getFrequency()).isEqualTo(1.0);
        assertThat(result.getDimensions().getTaskRelevance()).isEqualTo(1.0);
        assertThat(result.getDimensions().getTimeDecay()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("score(MemoryRecord, ScoringContext) 5 维度全 0.5 应返回 MEDIUM 等级")
    void should_ReturnMedium_When_AllDimensionsMedium() {
        ImportanceScorerImpl scorer = createScorer();
        // recallCount=5 -> frequency=0.5
        MemoryRecord record = buildRecord("mem_med", 5, Instant.now().minus(15, ChronoUnit.DAYS),
                "关键词A");
        // emotion=0.5, taskKeywords 部分命中 -> taskRelevance=0.5, createdAt 15 天前 -> timeDecay≈0.6
        ScoringContext context = new ScoringContext(
                Set.of("关键词A", "关键词B"), null, Instant.now(), 0.5);

        ImportanceResult result = scorer.score(record, context);

        // score 应在 MEDIUM 区间 [0.4, 0.7)
        assertThat(result.getScore()).isBetween(0.4, 0.7);
        assertThat(result.getLevel()).isEqualTo("MEDIUM");
        assertThat(result.getDimensions().getEmotionIntensity()).isEqualTo(0.5);
        assertThat(result.getDimensions().getFrequency()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("score(MemoryRecord, ScoringContext) 5 维度全低应返回 LOW 等级")
    void should_ReturnLow_When_AllDimensionsLow() {
        ImportanceScorerImpl scorer = createScorer();
        // recallCount=0 -> frequency=0.0
        // createdAt 60 天前 -> timeDecay = exp(-60/30) = exp(-2) ≈ 0.135
        MemoryRecord record = buildRecord("mem_low", 0, Instant.now().minus(60, ChronoUnit.DAYS),
                "无关词");
        // emotion=0.1, taskKeywords 不命中 -> taskRelevance=0.0
        ScoringContext context = new ScoringContext(
                Set.of("订单", "支付"), null, Instant.now(), 0.1);

        ImportanceResult result = scorer.score(record, context);

        assertThat(result.getScore()).isLessThan(0.4);
        assertThat(result.getLevel()).isEqualTo("LOW");
        assertThat(result.getDimensions().getFrequency()).isEqualTo(0.0);
        assertThat(result.getDimensions().getTaskRelevance()).isEqualTo(0.0);
        assertThat(result.getDimensions().getTimeDecay()).isLessThan(0.2);
    }

    @Test
    @DisplayName("5 维度权重和应等于 1.0")
    void should_HaveWeightsSumToOne() {
        double sum = ImportanceScorerImpl.W_EMOTION
                + ImportanceScorerImpl.W_FREQUENCY
                + ImportanceScorerImpl.W_NOVELTY
                + ImportanceScorerImpl.W_TASK_RELEVANCE
                + ImportanceScorerImpl.W_TIME_DECAY;
        assertThat(sum).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("时间衰减：30 天前的记录 timeDecay 应低于今天的记录")
    void should_ReduceScore_When_TimeDecay() {
        ImportanceScorerImpl scorer = createScorer();
        Instant now = Instant.now();

        MemoryRecord recentRecord = buildRecord("mem_recent", 5, now, "订单");
        MemoryRecord oldRecord = buildRecord("mem_old", 5, now.minus(30, ChronoUnit.DAYS), "订单");

        ScoringContext context = new ScoringContext(Set.of("订单"), null, now, 0.5);

        ImportanceResult recentResult = scorer.score(recentRecord, context);
        ImportanceResult oldResult = scorer.score(oldRecord, context);

        // 30 天前的 timeDecay 应低于今天的
        assertThat(oldResult.getDimensions().getTimeDecay())
                .isLessThan(recentResult.getDimensions().getTimeDecay());
        // 今天的 timeDecay = exp(0) = 1.0
        assertThat(recentResult.getDimensions().getTimeDecay()).isCloseTo(1.0, within(1e-6));
        // 30 天前的 timeDecay = exp(-1) ≈ 0.368
        assertThat(oldResult.getDimensions().getTimeDecay()).isCloseTo(Math.exp(-1), within(1e-3));
        // 总分也应更低
        assertThat(oldResult.getScore()).isLessThan(recentResult.getScore());
    }

    @Test
    @DisplayName("任务相关性：关键词命中应提升 taskRelevance 维度")
    void should_BoostTaskRelevance_When_KeywordsMatch() {
        ImportanceScorerImpl scorer = createScorer();
        Instant now = Instant.now();

        MemoryRecord matchedRecord = buildRecord("mem_match", 5, now, "订单,支付,查询");
        MemoryRecord unmatchedRecord = buildRecord("mem_unmatch", 5, now, "天气,新闻,体育");

        ScoringContext context = new ScoringContext(Set.of("订单", "支付", "查询"), null, now, 0.5);

        ImportanceResult matchedResult = scorer.score(matchedRecord, context);
        ImportanceResult unmatchedResult = scorer.score(unmatchedRecord, context);

        // 命中记录的 taskRelevance 应为 1.0（3/3 命中）
        assertThat(matchedResult.getDimensions().getTaskRelevance()).isEqualTo(1.0);
        // 未命中记录的 taskRelevance 应为 0.0
        assertThat(unmatchedResult.getDimensions().getTaskRelevance()).isEqualTo(0.0);
        // 命中记录的总分应高于未命中记录
        assertThat(matchedResult.getScore()).isGreaterThan(unmatchedResult.getScore());
    }

    // ============ 边界 case ============

    @Test
    @DisplayName("score 对 null record 应返回 LOW 默认结果")
    void should_ReturnLowDefault_When_RecordNull() {
        ImportanceScorerImpl scorer = createScorer();
        ImportanceResult result = scorer.score(null, ScoringContext.empty());
        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getLevel()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("score 对 null context 应使用默认值评分")
    void should_UseDefaults_When_ContextNull() {
        ImportanceScorerImpl scorer = createScorer();
        MemoryRecord record = buildRecord("mem_default", 5, Instant.now(), "订单");
        ImportanceResult result = scorer.score(record, null);
        assertThat(result).isNotNull();
        assertThat(result.getScore()).isBetween(0.0, 1.0);
        // emotionIntensity 默认 0.5, novelty 默认 0.5, taskRelevance 默认 0.5
        assertThat(result.getDimensions().getEmotionIntensity()).isEqualTo(0.5);
        assertThat(result.getDimensions().getNovelty()).isEqualTo(0.5);
        assertThat(result.getDimensions().getTaskRelevance()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("score 应返回各维度明细 dimensions")
    void should_ReturnDimensionBreakdown() {
        ImportanceScorerImpl scorer = createScorer();
        MemoryRecord record = buildRecord("mem_breakdown", 10, Instant.now(), "订单");
        ScoringContext context = new ScoringContext(Set.of("订单"), null, Instant.now(), 0.8);

        ImportanceResult result = scorer.score(record, context);

        assertThat(result.getDimensions()).isNotNull();
        assertThat(result.getDimensions().getEmotionIntensity()).isEqualTo(0.8);
        assertThat(result.getDimensions().getFrequency()).isEqualTo(1.0);
        assertThat(result.getDimensions().getTaskRelevance()).isEqualTo(1.0);
    }

    // ============ 旧接口 score(int, double, double) 向后兼容 ============

    @Test
    @DisplayName("旧 score(int, double, double) 应按 0.4*relevance + 0.3*recency + 0.3*freqNorm 加权计算")
    void should_ReturnWeightedScore_When_AllInputsProvided_Legacy() {
        ImportanceScorerImpl scorer = createScorer();
        // accessCount=10 -> freqNorm=1.0
        // score = 0.4*0.8 + 0.3*0.6 + 0.3*1.0 = 0.32 + 0.18 + 0.30 = 0.80
        double score = scorer.score(10, 0.6, 0.8);
        assertThat(score).isCloseTo(0.80, within(1e-6));
    }

    @Test
    @DisplayName("旧 score 应将 accessCount 饱和到 10 次，超出不再加分")
    void should_SaturateFreq_When_AccessCountExceedsTen_Legacy() {
        ImportanceScorerImpl scorer = createScorer();
        double s10 = scorer.score(10, 1.0, 1.0);
        double s100 = scorer.score(100, 1.0, 1.0);
        assertThat(s100).isEqualTo(s10);
        assertThat(s10).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("旧 score 应将超出 [0,1] 的 recency/relevance 截断到 [0,1]")
    void should_ClampInputs_When_RecencyOrRelevanceOutOfRange_Legacy() {
        ImportanceScorerImpl scorer = createScorer();
        // relevance=2.0 -> clamp to 1.0; recency=-0.5 -> clamp to 0.0; accessCount=0 -> freqNorm=0
        // score = 0.4*1.0 + 0.3*0.0 + 0.3*0.0 = 0.4
        double score = scorer.score(0, -0.5, 2.0);
        assertThat(score).isCloseTo(0.4, within(1e-6));
    }
}
