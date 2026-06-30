package com.agent.planning.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PlanComplexity enum unit tests (fromCode + fromNumeric branch coverage).
 */
@DisplayName("PlanComplexity 复杂度枚举")
class PlanComplexityTest {

    @Test
    @DisplayName("fromCode 正确解析各复杂度码")
    void should_ReturnCorrectComplexity_When_CodeValid() {
        assertThat(PlanComplexity.fromCode("l1")).isEqualTo(PlanComplexity.L1);
        assertThat(PlanComplexity.fromCode("l2")).isEqualTo(PlanComplexity.L2);
        assertThat(PlanComplexity.fromCode("l3")).isEqualTo(PlanComplexity.L3);
    }

    @Test
    @DisplayName("fromCode 大小写不敏感")
    void should_BeCaseInsensitive_When_CodeMixedCase() {
        assertThat(PlanComplexity.fromCode("L1")).isEqualTo(PlanComplexity.L1);
        assertThat(PlanComplexity.fromCode("L3")).isEqualTo(PlanComplexity.L3);
    }

    @Test
    @DisplayName("fromCode null 或空串兜底为 L1")
    void should_ReturnL1_When_CodeNullOrEmpty() {
        assertThat(PlanComplexity.fromCode(null)).isEqualTo(PlanComplexity.L1);
        assertThat(PlanComplexity.fromCode("")).isEqualTo(PlanComplexity.L1);
    }

    @Test
    @DisplayName("fromCode 未知 code 兜底为 L1")
    void should_ReturnL1_When_CodeUnknown() {
        assertThat(PlanComplexity.fromCode("l4")).isEqualTo(PlanComplexity.L1);
        assertThat(PlanComplexity.fromCode("xyz")).isEqualTo(PlanComplexity.L1);
    }

    @Test
    @DisplayName("fromNumeric 正确解析数字")
    void should_ReturnCorrectComplexity_When_NumericValid() {
        assertThat(PlanComplexity.fromNumeric(1)).isEqualTo(PlanComplexity.L1);
        assertThat(PlanComplexity.fromNumeric(2)).isEqualTo(PlanComplexity.L2);
        assertThat(PlanComplexity.fromNumeric(3)).isEqualTo(PlanComplexity.L3);
    }

    @Test
    @DisplayName("fromNumeric 超范围兜底为 L1")
    void should_ReturnL1_When_NumericOutOfRange() {
        assertThat(PlanComplexity.fromNumeric(0)).isEqualTo(PlanComplexity.L1);
        assertThat(PlanComplexity.fromNumeric(4)).isEqualTo(PlanComplexity.L1);
        assertThat(PlanComplexity.fromNumeric(-1)).isEqualTo(PlanComplexity.L1);
    }

    @Test
    @DisplayName("getNumeric 返回对应数值")
    void should_ReturnNumeric_When_Called() {
        assertThat(PlanComplexity.L1.getNumeric()).isEqualTo(1);
        assertThat(PlanComplexity.L2.getNumeric()).isEqualTo(2);
        assertThat(PlanComplexity.L3.getNumeric()).isEqualTo(3);
    }
}
