package com.agent.knowledge.api;

import com.agent.knowledge.model.KnowledgeQuery;
import com.agent.knowledge.model.SearchResult;

import java.util.List;

/**
 * Knowledge retriever (doc 07-knowledge §7.1, PRD §二(二) retrieval).
 *
 * <p>Searches chunks by query embedding + vector similarity + optional MMR reranking.
 * Skeleton stage: keyword-based substring matching. Milvus + MMR deferred to Plan 08 T10.</p>
 */
public interface KnowledgeRetriever {

    /**
     * Search chunks in KB.
     *
     * @param query search query (kbId / query text / topK / mmr flag)
     * @return list of SearchResult sorted by score descending
     */
    List<SearchResult> search(KnowledgeQuery query);

    /**
     * Index a chunk for retrieval (skeleton: stores in memory).
     *
     * @param kbId    knowledge base id
     * @param chunkId chunk identifier
     * @param docId   document id
     * @param content chunk text content
     */
    void indexChunk(String kbId, String chunkId, String docId, String content);

    /**
     * Remove indexed chunks for a document.
     *
     * @param kbId knowledge base id
     * @param docId document id
     * @return number of chunks removed
     */
    int removeByDocId(String kbId, String docId);
}
