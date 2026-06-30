package com.agent.planning.api.impl;

import com.agent.planning.enums.PlanComplexity;
import com.agent.planning.model.ComplexityDimensions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ComplexityScorerImpl unit tests (doc 03-task-engine §6.2 assessor).
 */
@DisplayName("ComplexityScorerImpl 复杂度评分器")
class ComplexityScorerImplTest {

    private final ComplexityScorerImpl scorer = new ComplexityScorerImpl();

    @Test
    @DisplayName("总分 ≤8 返回 L1")
    void should_ReturnL1_When_TotalScoreLe8() {
        // 1+1+1+1+1+1 = 6 ≤8 → L1
        ComplexityDimensions dims = new ComplexityDimensions(1, 1, 1, 1, 1, 1);
        assertThat(scorer.score(dims)).isEqualTo(PlanComplexity.L1);
    }

    @Test
    @DisplayName("总分 =8 边界返回 L1")
    void should_ReturnL1_When_TotalScoreAtBoundary8() {
        // 2+2+1+1+1+1 = 8 → L1 (boundary)
        ComplexityDimensions dims = new ComplexityDimensions(2, 2, 1, 1, 1, 1);
        assertThat(scorer.score(dims)).isEqualTo(PlanComplexity.L1);
    }

    @Test
    @DisplayName("总分 9-14 返回 L2")
    void should_ReturnL2_When_TotalScoreBetween9And14() {
        // 2+2+2+2+2+2 = 12 → L2
        ComplexityDimensions dims = new ComplexityDimensions(2, 2, 2, 2, 2, 2);
        assertThat(scorer.score(dims)).isEqualTo(PlanComplexity.L2);
    }

    @Test
    @DisplayName("总分 =14 边界返回 L2")
    void should_ReturnL2_When_TotalScoreAtBoundary14() {
        // 3+3+2+2+2+2 = 14 → L2 (boundary)
        ComplexityDimensions dims = new ComplexityDimensions(3, 3, 2, 2, 2, 2);
        assertThat(scorer.score(dims)).isEqualTo(PlanComplexity.L2);
    }

    @Test
    @DisplayName("总分 >14 返回 L3")
    void should_ReturnL3_When_TotalScoreGt14() {
        // 3+3+3+3+3+3 = 18 >14 → L3
        ComplexityDimensions dims = new ComplexityDimensions(3, 3, 3, 3, 3, 3);
        assertThat(scorer.score(dims)).isEqualTo(PlanComplexity.L3);
    }

    @Test
    @DisplayName("风险 ≥4 强制升级为 L3 (即使总分低)")
    void should_ForceUpgradeToL3_When_RiskLevelHigh() {
        // total = 1+1+1+1+4+1 = 9 (本应 L2), 但 risk=4 ≥4 → L3
        ComplexityDimensions dims = new ComplexityDimensions(1, 1, 1, 1, 4, 1);
        assertThat(scorer.score(dims)).isEqualTo(PlanComplexity.L3);
    }

    @Test
    @DisplayName("风险 =5 最高级强制升级为 L3")
    void should_ForceUpgradeToL3_When_RiskLevelMax() {
        // total = 1+1+1+1+5+1 = 10 (本应 L2), 但 risk=5 ≥4 → L3
        ComplexityDimensions dims = new ComplexityDimensions(1, 1, 1, 1, 5, 1);
        assertThat(scorer.score(dims)).isEqualTo(PlanComplexity.L3);
    }

    @Test
    @DisplayName("风险 =3 低于升级阈值, 按总分判级")
    void should_NotForceUpgrade_When_RiskBelowThreshold() {
        // total = 1+1+1+1+3+1 = 8 ≤8 → L1, risk=3 <4 不升级
        ComplexityDimensions dims = new ComplexityDimensions(1, 1, 1, 1, 3, 1);
        assertThat(scorer.score(dims)).isEqualTo(PlanComplexity.L1);
    }

    @Test
    @DisplayName("null dimensions 兜底返回 L1")
    void should_ReturnL1_When_DimensionsNull() {
        assertThat(scorer.score(null)).isEqualTo(PlanComplexity.L1);
    }

    @Test
    @DisplayName("负数维度值被 clamp 到 0 后再求和")
    void should_ClampNegativeToZero_When_DimensionNegative() {
        // -1 clamped to 0: 0+0+0+0+0+0 = 0 ≤8 → L1, risk=0 <4 不升级
        ComplexityDimensions dims = new ComplexityDimensions(-1, -1, -1, -1, -1, -1);
        assertThat(scorer.score(dims)).isEqualTo(PlanComplexity.L1);
    }

    @Test
    @DisplayName("超过 5 的维度值被 clamp 到 5")
    void should_ClampHighToFive_When_DimensionExceeds5() {
        // 100 clamped to 5 each: 5+5+5+5+5+5 = 30 >14 → L3 (但 risk=5 ≥4 也会触发升级)
        ComplexityDimensions dims = new ComplexityDimensions(100, 100, 100, 100, 100, 100);
        assertThat(scorer.score(dims)).isEqualTo(PlanComplexity.L3);
    }

    @Test
    @DisplayName("全 0 维度返回 L1")
    void should_ReturnL1_When_AllDimensionsZero() {
        ComplexityDimensions dims = new ComplexityDimensions(0, 0, 0, 0, 0, 0);
        assertThat(scorer.score(dims)).isEqualTo(PlanComplexity.L1);
    }
}
