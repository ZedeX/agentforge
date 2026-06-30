package com.agent.memory.api.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ImportanceScorerImplTest {

    @Test
    @DisplayName("score 应按 0.4*relevance + 0.3*recency + 0.3*freqNorm 加权计算")
    void should_ReturnWeightedScore_When_AllInputsProvided() {
        ImportanceScorerImpl scorer = new ImportanceScorerImpl();
        // accessCount=10 -> freqNorm=1.0
        // score = 0.4*0.8 + 0.3*0.6 + 0.3*1.0 = 0.32 + 0.18 + 0.30 = 0.80
        double score = scorer.score(10, 0.6, 0.8);
        assertThat(score).isCloseTo(0.80, within(1e-6));
    }

    @Test
    @DisplayName("score 应将 accessCount 饱和到 10 次，超出不再加分")
    void should_SaturateFreq_When_AccessCountExceedsTen() {
        ImportanceScorerImpl scorer = new ImportanceScorerImpl();
        double s10 = scorer.score(10, 1.0, 1.0);
        double s100 = scorer.score(100, 1.0, 1.0);
        assertThat(s100).isEqualTo(s10);
        assertThat(s10).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("score 应将超出 [0,1] 的 recency/relevance 截断到 [0,1]")
    void should_ClampInputs_When_RecencyOrRelevanceOutOfRange() {
        ImportanceScorerImpl scorer = new ImportanceScorerImpl();
        // relevance=2.0 -> clamp to 1.0; recency=-0.5 -> clamp to 0.0; accessCount=0 -> freqNorm=0
        // score = 0.4*1.0 + 0.3*0.0 + 0.3*0.0 = 0.4
        double score = scorer.score(0, -0.5, 2.0);
        assertThat(score).isCloseTo(0.4, within(1e-6));
    }
}