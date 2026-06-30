package com.agent.knowledge.api.impl;

import com.agent.knowledge.model.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VectorStoreImpl unit tests (doc 07-knowledge §6).
 */
@DisplayName("VectorStoreImpl 向量存储")
class VectorStoreImplTest {

    private final VectorStoreImpl store = new VectorStoreImpl();

    @Test
    @DisplayName("ensureCollection 创建集合, null kbId 安全跳过")
    void should_CreateCollection_When_EnsureCollectionCalled() {
        store.ensureCollection("kb-1", 1024);
        store.ensureCollection("kb-1", 1024); // idempotent, no throw
        store.ensureCollection(null, 1024); // null safe
    }

    @Test
    @DisplayName("upsert 写入向量后 search 返回按 score 降序的结果")
    void should_ReturnSortedResults_When_UpsertedAndSearched() {
        float[] queryVec = new float[]{1f, 0f, 0f};
        float[] vecA = new float[]{0.9f, 0.1f, 0f};   // close to query
        float[] vecB = new float[]{0.1f, 0.9f, 0f};   // far from query
        float[] vecC = new float[]{0.8f, 0.2f, 0f};   // medium

        store.upsert("kb-2", "chunk-a", vecA, "content-a", "doc-1");
        store.upsert("kb-2", "chunk-b", vecB, "content-b", "doc-1");
        store.upsert("kb-2", "chunk-c", vecC, "content-c", "doc-2");

        List<SearchResult> results = store.search("kb-2", queryVec, 3);
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getChunkId()).isEqualTo("chunk-a");
        assertThat(results.get(0).getScore()).isGreaterThan(results.get(1).getScore());
    }

    @Test
    @DisplayName("search null kbId / null vector / topK<=0 返回空列表")
    void should_ReturnEmpty_When_SearchParamsInvalid() {
        assertThat(store.search(null, new float[]{1f}, 5)).isEmpty();
        assertThat(store.search("kb-x", null, 5)).isEmpty();
        assertThat(store.search("kb-x", new float[]{1f}, 0)).isEmpty();
        assertThat(store.search("kb-x", new float[]{1f}, -1)).isEmpty();
    }

    @Test
    @DisplayName("search 集合不存在或为空返回空列表")
    void should_ReturnEmpty_When_CollectionNotFound() {
        assertThat(store.search("nonexistent", new float[]{1f, 0f}, 5)).isEmpty();
    }

    @Test
    @DisplayName("upsert null kbId / chunkId / vector 安全跳过")
    void should_SkipUpsert_When_ParamsNull() {
        store.upsert(null, "c1", new float[]{1f}, "content", "doc-1");
        store.upsert("kb-3", null, new float[]{1f}, "content", "doc-1");
        store.upsert("kb-3", "c1", null, "content", "doc-1");
        // No entries stored → search returns empty
        assertThat(store.search("kb-3", new float[]{1f}, 5)).isEmpty();
    }

    @Test
    @DisplayName("deleteByDocId 删除匹配向量并返回删除数")
    void should_DeleteAndCount_When_DeleteByDocIdCalled() {
        store.upsert("kb-4", "c1", new float[]{1f}, "a", "doc-1");
        store.upsert("kb-4", "c2", new float[]{1f}, "b", "doc-2");
        store.upsert("kb-4", "c3", new float[]{1f}, "c", "doc-1");

        int removed = store.deleteByDocId("kb-4", "doc-1");
        assertThat(removed).isEqualTo(2);

        // Verify remaining
        List<SearchResult> results = store.search("kb-4", new float[]{1f}, 5);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getChunkId()).isEqualTo("c2");
    }

    @Test
    @DisplayName("deleteByDocId null 参数或集合不存在返回 0")
    void should_ReturnZero_When_DeleteByDocIdInvalid() {
        assertThat(store.deleteByDocId(null, "doc-x")).isZero();
        assertThat(store.deleteByDocId("kb-x", null)).isZero();
        assertThat(store.deleteByDocId("nonexistent", "doc-x")).isZero();
    }

    @Test
    @DisplayName("dropCollection 删除集合, 不存在返回 false")
    void should_DropCollection_When_Exists() {
        store.upsert("kb-5", "c1", new float[]{1f}, "a", "doc-1");
        assertThat(store.dropCollection("kb-5")).isTrue();
        assertThat(store.dropCollection("kb-5")).isFalse();
        assertThat(store.dropCollection(null)).isFalse();
        assertThat(store.dropCollection("nonexistent")).isFalse();
    }
}
