package com.agent.knowledge.api.impl;

import com.agent.knowledge.enums.IngestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IngestStatus enum fromCode unit tests (doc 07-knowledge §3.2 IngestStatus).
 *
 * <p>Covers all fromCode branches: known codes, null, empty, unrecognized.</p>
 */
@DisplayName("IngestStatus 枚举 fromCode 解析")
class IngestStatusTest {

    @Test
    @DisplayName("已知 code 正确解析为对应枚举 (大小写不敏感)")
    void should_ReturnCorrectStatus_When_CodeKnown() {
        assertThat(IngestStatus.fromCode("pending")).isEqualTo(IngestStatus.PENDING);
        assertThat(IngestStatus.fromCode("VECTORIZED")).isEqualTo(IngestStatus.VECTORIZED);
        assertThat(IngestStatus.fromCode("failed")).isEqualTo(IngestStatus.FAILED);
    }

    @Test
    @DisplayName("null 或空 code 兜底为 PENDING")
    void should_DefaultToPending_When_CodeNullOrEmpty() {
        assertThat(IngestStatus.fromCode(null)).isEqualTo(IngestStatus.PENDING);
        assertThat(IngestStatus.fromCode("")).isEqualTo(IngestStatus.PENDING);
    }

    @Test
    @DisplayName("未知 code 兜底为 PENDING")
    void should_DefaultToPending_When_CodeUnrecognized() {
        assertThat(IngestStatus.fromCode("processing")).isEqualTo(IngestStatus.PENDING);
        assertThat(IngestStatus.fromCode("done")).isEqualTo(IngestStatus.PENDING);
    }

    @Test
    @DisplayName("getCode / getDescription 返回正确值")
    void should_ReturnCodeAndDescription_When_Called() {
        assertThat(IngestStatus.PENDING.getCode()).isEqualTo("pending");
        assertThat(IngestStatus.PENDING.getDescription()).isNotEmpty();
        assertThat(IngestStatus.VECTORIZED.getCode()).isEqualTo("vectorized");
        assertThat(IngestStatus.FAILED.getCode()).isEqualTo("failed");
    }
}
