package com.agent.knowledge.api.impl;

import com.agent.knowledge.api.EmbeddingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * In-memory embedding service fallback (doc 07-knowledge §5.2, Plan 08 T10).
 *
 * <p>Deterministic hash-based mock (1024-dim normalized vector).
 * Active when {@code knowledge.milvus.enabled=false} (default) or property is missing.
 * Real embedding API (bge-large-zh / OpenAI) is provided by
 * {@link com.agent.knowledge.milvus.MilvusEmbeddingServiceImpl} when Milvus is enabled.</p>
 */
@Component
@ConditionalOnProperty(name = "knowledge.milvus.enabled", havingValue = "false", matchIfMissing = true)
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final int DEFAULT_DIMENSION = 1024;

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DEFAULT_DIMENSION];
        if (text == null || text.isEmpty()) {
            return vector;
        }
        long seed = text.hashCode();
        Random rng = new Random(seed);
        for (int i = 0; i < DEFAULT_DIMENSION; i++) {
            vector[i] = rng.nextFloat() * 2f - 1f;
        }
        // Normalize to unit length for cosine similarity
        float norm = 0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < DEFAULT_DIMENSION; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        List<float[]> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(embed(text));
        }
        return result;
    }

    @Override
    public int getDimension() {
        return DEFAULT_DIMENSION;
    }
}
