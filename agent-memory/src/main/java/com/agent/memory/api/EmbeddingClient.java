package com.agent.memory.api;

import com.agent.memory.model.EmbeddingVector;

/**
 * Embedding client port (F12.D5: write-time vectorization, bge-large-zh 1024 dim).
 */
public interface EmbeddingClient {

    /**
     * Generate embedding vector for text.
     */
    EmbeddingVector embed(String text);
}
