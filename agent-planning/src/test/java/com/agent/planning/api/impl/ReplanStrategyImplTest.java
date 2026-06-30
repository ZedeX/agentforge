package com.agent.planning.api.impl;

import com.agent.planning.enums.ReplanMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReplanStrategyImpl unit tests (doc 03-task-engine §10.1 ReplanModeSelector).
 */
@DisplayName("ReplanStrategyImpl 重规划策略选择器")
class ReplanStrategyImplTest {

    private final ReplanStrategyImpl strategy = new ReplanStrategyImpl();

    @Test
    @DisplayName("子任务失败 (subtask_failed) 返回 INCREMENTAL")
    void should_ReturnIncremental_When_SinglePointFailure() {
        assertThat(strategy.select("subtask_failed", 0)).isEqualTo(ReplanMode.INCREMENTAL);
    }

    @Test
    @DisplayName("root 变更返回 FULL")
    void should_ReturnFull_When_RootNodeChanged() {
        assertThat(strategy.select("root_changed", 0)).isEqualTo(ReplanMode.FULL);
    }

    @Test
    @DisplayName("structural 变更返回 FULL")
    void should_ReturnFull_When_StructuralChange() {
        assertThat(strategy.select("structural_error", 0)).isEqualTo(ReplanMode.FULL);
    }

    @Test
    @DisplayName("replanCount ≥3 返回 MANUAL")
    void should_ReturnManual_When_ReplanCountExceedsLimit() {
        assertThat(strategy.select("subtask_failed", 3)).isEqualTo(ReplanMode.MANUAL);
    }

    @Test
    @DisplayName("structural + replanCount ≥2 返回 MANUAL")
    void should_ReturnManual_When_StructuralAndCountAtThreshold() {
        assertThat(strategy.select("structural_error", 2)).isEqualTo(ReplanMode.MANUAL);
    }

    @Test
    @DisplayName("structural + replanCount <2 返回 FULL (未到 manual 阈值)")
    void should_ReturnFull_When_StructuralButCountBelowManualThreshold() {
        assertThat(strategy.select("structural_error", 1)).isEqualTo(ReplanMode.FULL);
    }

    @Test
    @DisplayName("未知原因返回 INCREMENTAL")
    void should_ReturnIncremental_When_ReasonUnknown() {
        assertThat(strategy.select("unknown_reason", 0)).isEqualTo(ReplanMode.INCREMENTAL);
    }

    @Test
    @DisplayName("null 原因返回 INCREMENTAL")
    void should_ReturnIncremental_When_ReasonNull() {
        assertThat(strategy.select(null, 0)).isEqualTo(ReplanMode.INCREMENTAL);
    }

    @Test
    @DisplayName("空原因返回 INCREMENTAL")
    void should_ReturnIncremental_When_ReasonEmpty() {
        assertThat(strategy.select("", 0)).isEqualTo(ReplanMode.INCREMENTAL);
    }

    @Test
    @DisplayName("ROOT 大写也能识别返回 FULL")
    void should_BeCaseInsensitive_When_ReasonHasRoot() {
        assertThat(strategy.select("ROOT_changed", 0)).isEqualTo(ReplanMode.FULL);
    }

    @Test
    @DisplayName("canReplan count<3 返回 true")
    void should_ReturnTrue_When_CountBelowLimit() {
        assertThat(strategy.canReplan(0)).isTrue();
        assertThat(strategy.canReplan(2)).isTrue();
    }

    @Test
    @DisplayName("canReplan count≥3 返回 false")
    void should_ReturnFalse_When_CountAtOrAboveLimit() {
        assertThat(strategy.canReplan(3)).isFalse();
        assertThat(strategy.canReplan(5)).isFalse();
    }
}
