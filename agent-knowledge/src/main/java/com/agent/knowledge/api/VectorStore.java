package com.agent.knowledge.api;

import com.agent.knowledge.model.SearchResult;

import java.util.List;

/**
 * Vector store (doc 07-knowledge §6, PRD §二(二) Milvus integration).
 *
 * <p>Stores + retrieves chunk embeddings by similarity. Each KB maps to a collection
 * (Milvus naming: kb_{kbId}). Skeleton stage: in-memory cosine similarity.
 * Milvus SDK integration deferred to Plan 08 T10.</p>
 */
public interface VectorStore {

    /**
     * Ensure collection exists for a KB (create if absent).
     *
     * @param kbId      knowledge base id
     * @param dimension vector dimension
     */
    void ensureCollection(String kbId, int dimension);

    /**
     * Upsert a vector + metadata into the collection.
     *
     * @param kbId    knowledge base id
     * @param chunkId chunk identifier (primary key)
     * @param vector  embedding vector
     * @param content chunk text content
     * @param docId   document id
     */
    void upsert(String kbId, String chunkId, float[] vector, String content, String docId);

    /**
     * Search topK similar vectors.
     *
     * @param kbId        knowledge base id
     * @param queryVector query embedding vector
     * @param topK        max results
     * @return list of SearchResult sorted by score descending
     */
    List<SearchResult> search(String kbId, float[] queryVector, int topK);

    /**
     * Delete all vectors for a document.
     *
     * @param kbId knowledge base id
     * @param docId document id
     * @return number of vectors deleted
     */
    int deleteByDocId(String kbId, String docId);

    /**
     * Drop the collection for a KB.
     *
     * @param kbId knowledge base id
     * @return true if dropped, false if not exists
     */
    boolean dropCollection(String kbId);
}
