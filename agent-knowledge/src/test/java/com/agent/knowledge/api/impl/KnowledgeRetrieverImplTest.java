package com.agent.knowledge.api.impl;

import com.agent.knowledge.model.KnowledgeQuery;
import com.agent.knowledge.model.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KnowledgeRetrieverImpl unit tests (doc 07-knowledge §7.1).
 */
@DisplayName("KnowledgeRetrieverImpl 知识检索器")
class KnowledgeRetrieverImplTest {

    private final KnowledgeRetrieverImpl retriever = new KnowledgeRetrieverImpl();

    @Test
    @DisplayName("indexChunk 后 search 命中, 按 score 降序排列")
    void should_ReturnSortedResults_When_ChunksIndexedAndSearched() {
        retriever.indexChunk("kb-1", "c1", "doc-1", "hello world foo");
        retriever.indexChunk("kb-1", "c2", "doc-1", "hello hello bar");
        retriever.indexChunk("kb-1", "c3", "doc-1", "unrelated content");

        KnowledgeQuery query = new KnowledgeQuery("kb-1", "hello", 5, false, 0.5);
        List<SearchResult> results = retriever.search(query);
        assertThat(results).hasSize(2);
        // c2 has "hello" twice → higher score
        assertThat(results.get(0).getChunkId()).isEqualTo("c2");
        assertThat(results.get(0).getScore()).isGreaterThan(results.get(1).getScore());
    }

    @Test
    @DisplayName("search topK 限制返回数量")
    void should_LimitResults_When_TopKSpecified() {
        retriever.indexChunk("kb-2", "c1", "d1", "hello one");
        retriever.indexChunk("kb-2", "c2", "d1", "hello two");
        retriever.indexChunk("kb-2", "c3", "d1", "hello three");

        KnowledgeQuery query = new KnowledgeQuery("kb-2", "hello", 2, false, 0.5);
        List<SearchResult> results = retriever.search(query);
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("null query / null kbId / null queryText 返回空列表")
    void should_ReturnEmpty_When_QueryNullOrKbIdNullOrQueryTextNull() {
        assertThat(retriever.search(null)).isEmpty();
        assertThat(retriever.search(new KnowledgeQuery(null, "hello", 5, false, 0.5))).isEmpty();
        assertThat(retriever.search(new KnowledgeQuery("kb-x", null, 5, false, 0.5))).isEmpty();
    }

    @Test
    @DisplayName("KB 不存在或无索引返回空列表")
    void should_ReturnEmpty_When_KbNotFound() {
        KnowledgeQuery query = new KnowledgeQuery("nonexistent-kb", "hello", 5, false, 0.5);
        assertThat(retriever.search(query)).isEmpty();
    }

    @Test
    @DisplayName("无匹配 term 返回空列表 (score 全为 0 被过滤)")
    void should_ReturnEmpty_When_NoMatch() {
        retriever.indexChunk("kb-3", "c1", "d1", "apple banana");
        KnowledgeQuery query = new KnowledgeQuery("kb-3", "orange", 5, false, 0.5);
        assertThat(retriever.search(query)).isEmpty();
    }

    @Test
    @DisplayName("indexChunk null 参数安全跳过")
    void should_SkipIndex_When_ParamsNull() {
        retriever.indexChunk(null, "c1", "d1", "content");
        retriever.indexChunk("kb-4", null, "d1", "content");
        retriever.indexChunk("kb-4", "c1", "d1", null);
        KnowledgeQuery query = new KnowledgeQuery("kb-4", "content", 5, false, 0.5);
        assertThat(retriever.search(query)).isEmpty();
    }

    @Test
    @DisplayName("removeByDocId 删除匹配 chunk 并返回删除数")
    void should_RemoveAndCount_When_DeleteByDocId() {
        retriever.indexChunk("kb-5", "c1", "doc-a", "hello");
        retriever.indexChunk("kb-5", "c2", "doc-b", "hello");
        retriever.indexChunk("kb-5", "c3", "doc-a", "world");

        int removed = retriever.removeByDocId("kb-5", "doc-a");
        assertThat(removed).isEqualTo(2);

        // Verify remaining
        KnowledgeQuery query = new KnowledgeQuery("kb-5", "hello", 5, false, 0.5);
        List<SearchResult> results = retriever.search(query);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getChunkId()).isEqualTo("c2");
    }

    @Test
    @DisplayName("removeByDocId null 参数或 KB 不存在返回 0")
    void should_ReturnZero_When_RemoveByDocIdInvalid() {
        assertThat(retriever.removeByDocId(null, "doc-x")).isZero();
        assertThat(retriever.removeByDocId("kb-x", null)).isZero();
        assertThat(retriever.removeByDocId("nonexistent", "doc-x")).isZero();
    }
}
