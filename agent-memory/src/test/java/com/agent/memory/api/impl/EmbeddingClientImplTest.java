package com.agent.memory.api.impl;

import com.agent.memory.model.EmbeddingVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingClientImplTest {

    @Test
    @DisplayName("embed 应返回 1024 维向量")
    void should_Return1024DimVector_When_EmbedInvoked() {
        EmbeddingClientImpl client = new EmbeddingClientImpl();
        EmbeddingVector v = client.embed("测试文本");
        assertThat(v).isNotNull();
        assertThat(v.getDim()).isEqualTo(1024);
        assertThat(v.getValues()).hasSize(1024);
    }

    @Test
    @DisplayName("embed 对相同文本应返回相同向量（确定性）")
    void should_ReturnSameVector_When_SameText() {
        EmbeddingClientImpl client = new EmbeddingClientImpl();
        EmbeddingVector a = client.embed("确定性测试");
        EmbeddingVector b = client.embed("确定性测试");
        assertThat(a.getValues()).containsExactly(b.getValues());
    }

    @Test
    @DisplayName("embed 对不同文本应返回不同向量")
    void should_ReturnDifferentVector_When_DifferentText() {
        EmbeddingClientImpl client = new EmbeddingClientImpl();
        EmbeddingVector a = client.embed("文本A");
        EmbeddingVector b = client.embed("文本B");
        assertThat(a.getValues()).isNotEqualTo(b.getValues());
    }

    @Test
    @DisplayName("embed 对 null 文本应仍返回 1024 维向量且不抛异常")
    void should_ReturnVector_When_TextIsNull() {
        EmbeddingClientImpl client = new EmbeddingClientImpl();
        EmbeddingVector v = client.embed(null);
        assertThat(v.getDim()).isEqualTo(1024);
    }
}