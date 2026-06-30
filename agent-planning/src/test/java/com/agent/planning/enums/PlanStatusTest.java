package com.agent.planning.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PlanStatus enum unit tests (fromCode branch coverage).
 */
@DisplayName("PlanStatus 计划状态枚举")
class PlanStatusTest {

    @Test
    @DisplayName("fromCode 正确解析各状态码")
    void should_ReturnCorrectStatus_When_CodeValid() {
        assertThat(PlanStatus.fromCode("draft")).isEqualTo(PlanStatus.DRAFT);
        assertThat(PlanStatus.fromCode("validated")).isEqualTo(PlanStatus.VALIDATED);
        assertThat(PlanStatus.fromCode("executing")).isEqualTo(PlanStatus.EXECUTING);
        assertThat(PlanStatus.fromCode("completed")).isEqualTo(PlanStatus.COMPLETED);
        assertThat(PlanStatus.fromCode("failed")).isEqualTo(PlanStatus.FAILED);
        assertThat(PlanStatus.fromCode("replanned")).isEqualTo(PlanStatus.REPLANNED);
    }

    @Test
    @DisplayName("fromCode 大小写不敏感")
    void should_BeCaseInsensitive_When_CodeMixedCase() {
        assertThat(PlanStatus.fromCode("DRAFT")).isEqualTo(PlanStatus.DRAFT);
        assertThat(PlanStatus.fromCode("Validated")).isEqualTo(PlanStatus.VALIDATED);
    }

    @Test
    @DisplayName("fromCode null 或空串兜底为 DRAFT")
    void should_ReturnDraft_When_CodeNullOrEmpty() {
        assertThat(PlanStatus.fromCode(null)).isEqualTo(PlanStatus.DRAFT);
        assertThat(PlanStatus.fromCode("")).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    @DisplayName("fromCode 未知 code 兜底为 DRAFT")
    void should_ReturnDraft_When_CodeUnknown() {
        assertThat(PlanStatus.fromCode("unknown")).isEqualTo(PlanStatus.DRAFT);
        assertThat(PlanStatus.fromCode("xyz")).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    @DisplayName("getCode 与 getDescription 返回正确值")
    void should_ReturnCodeAndDescription_When_Called() {
        assertThat(PlanStatus.DRAFT.getCode()).isEqualTo("draft");
        assertThat(PlanStatus.DRAFT.getDescription()).contains("草稿");
        assertThat(PlanStatus.COMPLETED.getCode()).isEqualTo("completed");
    }
}
