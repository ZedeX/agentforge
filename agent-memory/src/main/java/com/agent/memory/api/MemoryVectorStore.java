package com.agent.memory.api;

import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;

/**
 * Memory vector store port (F12.D5: insert vector into Milvus).
 */
public interface MemoryVectorStore {

    /**
     * Insert a memory record + its embedding vector into the vector store.
     */
    void insert(MemoryRecord record, EmbeddingVector vector);
}
