package com.agent.planning.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReplanMode enum unit tests (fromCode branch coverage).
 */
@DisplayName("ReplanMode 重规划模式枚举")
class ReplanModeTest {

    @Test
    @DisplayName("fromCode 正确解析各模式码")
    void should_ReturnCorrectMode_When_CodeValid() {
        assertThat(ReplanMode.fromCode("incremental")).isEqualTo(ReplanMode.INCREMENTAL);
        assertThat(ReplanMode.fromCode("full")).isEqualTo(ReplanMode.FULL);
        assertThat(ReplanMode.fromCode("manual")).isEqualTo(ReplanMode.MANUAL);
    }

    @Test
    @DisplayName("fromCode 大小写不敏感")
    void should_BeCaseInsensitive_When_CodeMixedCase() {
        assertThat(ReplanMode.fromCode("INCREMENTAL")).isEqualTo(ReplanMode.INCREMENTAL);
        assertThat(ReplanMode.fromCode("Full")).isEqualTo(ReplanMode.FULL);
    }

    @Test
    @DisplayName("fromCode null 或空串兜底为 INCREMENTAL")
    void should_ReturnIncremental_When_CodeNullOrEmpty() {
        assertThat(ReplanMode.fromCode(null)).isEqualTo(ReplanMode.INCREMENTAL);
        assertThat(ReplanMode.fromCode("")).isEqualTo(ReplanMode.INCREMENTAL);
    }

    @Test
    @DisplayName("fromCode 未知 code 兜底为 INCREMENTAL")
    void should_ReturnIncremental_When_CodeUnknown() {
        assertThat(ReplanMode.fromCode("unknown")).isEqualTo(ReplanMode.INCREMENTAL);
        assertThat(ReplanMode.fromCode("xyz")).isEqualTo(ReplanMode.INCREMENTAL);
    }

    @Test
    @DisplayName("getCode 与 getDescription 返回正确值")
    void should_ReturnCodeAndDescription_When_Called() {
        assertThat(ReplanMode.INCREMENTAL.getCode()).isEqualTo("incremental");
        assertThat(ReplanMode.FULL.getCode()).isEqualTo("full");
        assertThat(ReplanMode.MANUAL.getDescription()).contains("人工");
    }
}
