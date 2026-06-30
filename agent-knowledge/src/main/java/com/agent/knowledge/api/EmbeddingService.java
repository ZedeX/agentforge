package com.agent.knowledge.api;

import java.util.List;

/**
 * Embedding service (doc 07-knowledge §5.2, PRD §二(二) vectorization).
 *
 * <p>Converts text into dense float vectors for similarity search.
 * Skeleton stage: deterministic hash-based mock (1024-dim).
 * Real embedding API (bge-large-zh / OpenAI) deferred to Plan 08 T10.</p>
 */
public interface EmbeddingService {

    /**
     * Embed a single text into a float vector.
     *
     * @param text input text, null/empty returns zero vector
     * @return float vector of configured dimension (default 1024)
     */
    float[] embed(String text);

    /**
     * Embed a batch of texts.
     *
     * @param texts list of input texts
     * @return list of float vectors, matching input length
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * Get the embedding dimension.
     *
     * @return dimension (default 1024)
     */
    int getDimension();
}
