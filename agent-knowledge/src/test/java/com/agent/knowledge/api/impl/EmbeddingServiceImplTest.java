package com.agent.knowledge.api.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EmbeddingServiceImpl unit tests (doc 07-knowledge §5.2).
 */
@DisplayName("EmbeddingServiceImpl 向量化服务")
class EmbeddingServiceImplTest {

    private final EmbeddingServiceImpl service = new EmbeddingServiceImpl();

    @Test
    @DisplayName("embed 返回 1024 维向量")
    void should_Return1024DimVector_When_EmbedCalled() {
        float[] vector = service.embed("Hello World");
        assertThat(vector).hasSize(1024);
    }

    @Test
    @DisplayName("embed null 或空文本返回零向量")
    void should_ReturnZeroVector_When_TextNullOrEmpty() {
        float[] nullVec = service.embed(null);
        assertThat(nullVec).hasSize(1024);
        for (float v : nullVec) {
            assertThat(v).isZero();
        }

        float[] emptyVec = service.embed("");
        assertThat(emptyVec).hasSize(1024);
        for (float v : emptyVec) {
            assertThat(v).isZero();
        }
    }

    @Test
    @DisplayName("相同文本生成相同向量 (确定性)")
    void should_ReturnSameVector_When_SameTextEmbeddedTwice() {
        float[] v1 = service.embed("deterministic test");
        float[] v2 = service.embed("deterministic test");
        assertThat(v1).containsExactly(v2);
    }

    @Test
    @DisplayName("embedBatch 返回与输入等长的向量列表")
    void should_ReturnBatch_When_EmbedBatchCalled() {
        List<float[]> batch = service.embedBatch(List.of("a", "b", "c"));
        assertThat(batch).hasSize(3);
        for (float[] v : batch) {
            assertThat(v).hasSize(1024);
        }
    }

    @Test
    @DisplayName("embedBatch null 或空列表返回空列表")
    void should_ReturnEmpty_When_BatchNullOrEmpty() {
        assertThat(service.embedBatch(null)).isEmpty();
        assertThat(service.embedBatch(List.of())).isEmpty();
    }

    @Test
    @DisplayName("getDimension 返回 1024")
    void should_Return1024_When_GetDimensionCalled() {
        assertThat(service.getDimension()).isEqualTo(1024);
    }
}
