package com.agent.memory.api.impl;

import com.agent.memory.model.EmbeddingVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock 实现 {@link MockEmbeddingClientImpl} 单元测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>旧版 {@code embed(String)} → {@link EmbeddingVector} 包装</li>
 *   <li>T5 新增 {@code embed(String, String)} → float[]</li>
 *   <li>T5 新增 {@code embedBatch(List, String)} → List&lt;float[]&gt;</li>
 *   <li>确定性、可区分性、null 输入容错</li>
 * </ul>
 */
class MockEmbeddingClientImplTest {

    @Test
    @DisplayName("embed 应返回 1024 维向量（EmbeddingVector 包装）")
    void should_Return1024DimVector_When_EmbedInvoked() {
        MockEmbeddingClientImpl client = new MockEmbeddingClientImpl();
        EmbeddingVector v = client.embed("测试文本");
        assertThat(v).isNotNull();
        assertThat(v.getDim()).isEqualTo(1024);
        assertThat(v.getValues()).hasSize(1024);
    }

    @Test
    @DisplayName("embed 对相同文本应返回相同向量（确定性）")
    void should_ReturnSameVector_When_SameText() {
        MockEmbeddingClientImpl client = new MockEmbeddingClientImpl();
        EmbeddingVector a = client.embed("确定性测试");
        EmbeddingVector b = client.embed("确定性测试");
        assertThat(a.getValues()).containsExactly(b.getValues());
    }

    @Test
    @DisplayName("embed 对不同文本应返回不同向量")
    void should_ReturnDifferentVector_When_DifferentText() {
        MockEmbeddingClientImpl client = new MockEmbeddingClientImpl();
        EmbeddingVector a = client.embed("文本A");
        EmbeddingVector b = client.embed("文本B");
        assertThat(a.getValues()).isNotEqualTo(b.getValues());
    }

    @Test
    @DisplayName("embed 对 null 文本应仍返回 1024 维向量且不抛异常")
    void should_ReturnVector_When_TextIsNull() {
        MockEmbeddingClientImpl client = new MockEmbeddingClientImpl();
        EmbeddingVector v = client.embed(null);
        assertThat(v.getDim()).isEqualTo(1024);
    }

    @Test
    @DisplayName("embed(text, tenantId) 应返回 1024 维 float[]")
    void should_ReturnRawFloatArray_When_TenantIdProvided() {
        MockEmbeddingClientImpl client = new MockEmbeddingClientImpl();
        float[] v = client.embed("带租户的文本", "tenant-001");
        assertThat(v).hasSize(1024);
    }

    @Test
    @DisplayName("embedBatch 应返回与输入等长的 List<float[]>")
    void should_ReturnListWithSameSize_When_BatchInvoked() {
        MockEmbeddingClientImpl client = new MockEmbeddingClientImpl();
        List<float[]> result = client.embedBatch(
                List.of("文本1", "文本2", "文本3"), "tenant-001");
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).hasSize(1024);
        assertThat(result.get(1)).hasSize(1024);
        assertThat(result.get(2)).hasSize(1024);
    }

    @Test
    @DisplayName("embedBatch 空列表应返回空 List")
    void should_ReturnEmptyList_When_InputEmpty() {
        MockEmbeddingClientImpl client = new MockEmbeddingClientImpl();
        List<float[]> result = client.embedBatch(List.of(), "tenant-001");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("embedBatch 相同文本应返回相同向量（确定性）")
    void should_ReturnSameVectors_When_SameBatchText() {
        MockEmbeddingClientImpl client = new MockEmbeddingClientImpl();
        List<float[]> a = client.embedBatch(List.of("x", "y"), "t");
        List<float[]> b = client.embedBatch(List.of("x", "y"), "t");
        assertThat(a.get(0)).containsExactly(b.get(0));
        assertThat(a.get(1)).containsExactly(b.get(1));
    }

    @Test
    @DisplayName("embed(text) 与 embed(text, null) 应返回相同向量")
    void should_ReturnSameVector_WhetherUsingLegacyOrNewMethod() {
        MockEmbeddingClientImpl client = new MockEmbeddingClientImpl();
        EmbeddingVector legacy = client.embed("兼容性测试");
        float[] modern = client.embed("兼容性测试", null);
        assertThat(legacy.getValues()).containsExactly(modern);
    }

    @Test
    @DisplayName("embed(null, tenantId) 不应抛异常（与旧 embed(null) 行为一致）")
    void should_NotThrow_When_TextNullWithTenantId() {
        MockEmbeddingClientImpl client = new MockEmbeddingClientImpl();
        float[] v = client.embed(null, "tenant-001");
        assertThat(v).hasSize(1024);
    }

    @Test
    @DisplayName("embedBatch(null, tenantId) 应容错返回空 List（Mock 防御性）")
    void should_ReturnEmptyList_When_BatchInputIsNull() {
        MockEmbeddingClientImpl client = new MockEmbeddingClientImpl();
        // Mock 设计为容错：null 输入返回空 List（与空列表行为一致）
        // 注意：HTTP 实现对此会抛 IllegalArgumentException（更严格）
        List<float[]> result = client.embedBatch(null, "tenant-001");
        assertThat(result).isEmpty();
    }
}
