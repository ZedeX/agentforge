package com.agent.orchestrator.assessor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ComplexityScorer 单元测试（Red 阶段：实现尚未存在，预期编译失败）。
 *
 * <p>对齐 doc 03-task-engine §2.2 六维度打分模型与判级阈值。
 * 测试阈值以 docs/tests/unit-test-cases.md §6 UT-PLAN-001~004 为准：
 * 总分 0~18（无加权，goal+execution+domain+knowledge+risk+context 直接相加）。</p>
 *
 * <p>判级规则：</p>
 * <ul>
 *   <li>UT-PLAN-001: 总分 ≤ 8 → L1</li>
 *   <li>UT-PLAN-002: 总分 9~14 → L2</li>
 *   <li>UT-PLAN-003: 总分 &gt; 14 → L3</li>
 *   <li>UT-PLAN-004: 风险维度 = 3 时强制升级 L3（即使总分对应 L2）</li>
 * </ul>
 *
 * <p>注：设计文档 §2.2 原始阈值（加权 ≤4 / 5~9 / ≥10）与 unit-test-cases.md
 * （无加权 ≤8 / 9~14 / &gt;14）存在差异；本实现以 unit-test-cases.md 为准。</p>
 */
class ComplexityScorerTest {

    private final ComplexityScorer scorer = new ComplexityScorer();

    // ===== UT-PLAN-001: 总分 ≤ 8 → L1 =====

    @Test
    @DisplayName("UT-PLAN-001: 六维度总分=8（边界上界）应判级 L1")
    void should_ReturnL1_When_TotalScoreLe8() {
        // 总分 = 1+1+1+1+2+2 = 8（边界值）
        ComplexityDimensions dimensions = ComplexityDimensions.builder()
                .goal(1).execution(1).domain(1).knowledge(1).risk(2).context(2)
                .build();

        ComplexityLevel result = scorer.score(dimensions);

        assertThat(result).as("总分=8 应判级 L1").isEqualTo(ComplexityLevel.L1);
    }

    @Test
    @DisplayName("UT-PLAN-001 边界: 总分=0（全 0）应判级 L1")
    void should_ReturnL1_When_TotalScoreIsZero() {
        ComplexityDimensions dimensions = ComplexityDimensions.builder()
                .goal(0).execution(0).domain(0).knowledge(0).risk(0).context(0)
                .build();

        ComplexityLevel result = scorer.score(dimensions);

        assertThat(result).as("总分=0 应判级 L1").isEqualTo(ComplexityLevel.L1);
    }

    // ===== UT-PLAN-002: 总分 9~14 → L2 =====

    @Test
    @DisplayName("UT-PLAN-002: 六维度总分=9（边界下界）应判级 L2")
    void should_ReturnL2_When_TotalScoreBetween9And14() {
        // 总分 = 2+2+2+2+1+0 = 9（边界值）
        ComplexityDimensions dimensions = ComplexityDimensions.builder()
                .goal(2).execution(2).domain(2).knowledge(2).risk(1).context(0)
                .build();

        ComplexityLevel result = scorer.score(dimensions);

        assertThat(result).as("总分=9 应判级 L2").isEqualTo(ComplexityLevel.L2);
    }

    @Test
    @DisplayName("UT-PLAN-002 边界: 总分=14（边界上界，risk=0）应判级 L2")
    void should_ReturnL2_When_TotalScoreIs14AndRiskIsZero() {
        // 总分 = 3+3+3+3+0+2 = 14（边界值，risk=0 不触发强制升级）
        ComplexityDimensions dimensions = ComplexityDimensions.builder()
                .goal(3).execution(3).domain(3).knowledge(3).risk(0).context(2)
                .build();

        ComplexityLevel result = scorer.score(dimensions);

        assertThat(result).as("总分=14 且 risk=0 应判级 L2（不触发强制升级）")
                .isEqualTo(ComplexityLevel.L2);
    }

    // ===== UT-PLAN-003: 总分 > 14 → L3 =====

    @Test
    @DisplayName("UT-PLAN-003: 六维度总分=15（边界下界）应判级 L3")
    void should_ReturnL3_When_TotalScoreGt14() {
        // 总分 = 3+3+3+3+3+0 = 15（边界值）
        ComplexityDimensions dimensions = ComplexityDimensions.builder()
                .goal(3).execution(3).domain(3).knowledge(3).risk(3).context(0)
                .build();

        ComplexityLevel result = scorer.score(dimensions);

        assertThat(result).as("总分=15 应判级 L3").isEqualTo(ComplexityLevel.L3);
    }

    @Test
    @DisplayName("UT-PLAN-003 边界: 总分=18（满分）应判级 L3")
    void should_ReturnL3_When_TotalScoreIsMax() {
        // 总分 = 3+3+3+3+3+3 = 18（满分）
        ComplexityDimensions dimensions = ComplexityDimensions.builder()
                .goal(3).execution(3).domain(3).knowledge(3).risk(3).context(3)
                .build();

        ComplexityLevel result = scorer.score(dimensions);

        assertThat(result).as("总分=18 应判级 L3").isEqualTo(ComplexityLevel.L3);
    }

    // ===== UT-PLAN-004: 风险维度 = 3 时强制升级 L3 =====

    @Test
    @DisplayName("UT-PLAN-004: 风险维度=3 且总分=10（本应 L2）应强制升级 L3")
    void should_ForceUpgradeToL3_When_RiskLevelIsHigh() {
        // 总分 = 1+1+1+1+3+3 = 10（本应 L2），但 risk=3 强制升级 L3
        ComplexityDimensions dimensions = ComplexityDimensions.builder()
                .goal(1).execution(1).domain(1).knowledge(1).risk(3).context(3)
                .build();

        ComplexityLevel result = scorer.score(dimensions);

        assertThat(result).as("risk=3 时即使总分=10（L2 范围）也应强制升级 L3")
                .isEqualTo(ComplexityLevel.L3);
    }

    @Test
    @DisplayName("UT-PLAN-004 边界: 风险维度=3 且总分=5（本应 L2 下界）应强制升级 L3")
    void should_ForceUpgradeToL3_When_RiskIsHighAndScoreIsLow() {
        // 总分 = 0+0+0+2+3+0 = 5（L2 范围），但 risk=3 强制升级 L3
        ComplexityDimensions dimensions = ComplexityDimensions.builder()
                .goal(0).execution(0).domain(0).knowledge(2).risk(3).context(0)
                .build();

        ComplexityLevel result = scorer.score(dimensions);

        assertThat(result).as("risk=3 时即使总分=5（L2 范围）也应强制升级 L3")
                .isEqualTo(ComplexityLevel.L3);
    }

    @Test
    @DisplayName("UT-PLAN-004 补充: 风险维度=3 且执行维度=3（双高）应强制 L3")
    void should_ReturnL3_When_ExecutionAndRiskBothHigh() {
        // 总分 = 0+3+0+0+3+0 = 6（L2 范围），execution=3 且 risk=3 强制 L3
        ComplexityDimensions dimensions = ComplexityDimensions.builder()
                .goal(0).execution(3).domain(0).knowledge(0).risk(3).context(0)
                .build();

        ComplexityLevel result = scorer.score(dimensions);

        assertThat(result).as("execution=3 且 risk=3 应强制 L3")
                .isEqualTo(ComplexityLevel.L3);
    }

    @Test
    @DisplayName("UT-PLAN-004 反例: 风险维度=2（非高危）时不触发强制升级，按总分判级")
    void should_NotForceUpgrade_When_RiskLevelIsNotHigh() {
        // 总分 = 1+1+1+1+2+2 = 8（L1 范围），risk=2 不触发强制升级
        ComplexityDimensions dimensions = ComplexityDimensions.builder()
                .goal(1).execution(1).domain(1).knowledge(1).risk(2).context(2)
                .build();

        ComplexityLevel result = scorer.score(dimensions);

        assertThat(result).as("risk=2（非高危）不触发强制升级，总分=8 判级 L1")
                .isEqualTo(ComplexityLevel.L1);
    }
}
