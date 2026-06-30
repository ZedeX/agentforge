package com.agent.knowledge.api.impl;

import com.agent.knowledge.api.VectorStore;
import com.agent.knowledge.model.SearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory vector store (doc 07-knowledge §6).
 *
 * <p>Skeleton stage: in-memory cosine similarity over ConcurrentHashMap per KB collection.
 * Milvus SDK integration (HNSW index / collection lifecycle) deferred to Plan 08 T10.</p>
 */
@Component
public class VectorStoreImpl implements VectorStore {

    private static class VectorEntry {
        final String chunkId;
        final String docId;
        final float[] vector;
        final String content;
        VectorEntry(String chunkId, String docId, float[] vector, String content) {
            this.chunkId = chunkId;
            this.docId = docId;
            this.vector = vector;
            this.content = content;
        }
    }

    private final Map<String, Map<String, VectorEntry>> collections = new ConcurrentHashMap<>();

    @Override
    public void ensureCollection(String kbId, int dimension) {
        if (kbId == null) {
            return;
        }
        collections.computeIfAbsent(kbId, k -> new ConcurrentHashMap<>());
    }

    @Override
    public void upsert(String kbId, String chunkId, float[] vector, String content, String docId) {
        if (kbId == null || chunkId == null || vector == null) {
            return;
        }
        Map<String, VectorEntry> collection = collections.computeIfAbsent(kbId, k -> new ConcurrentHashMap<>());
        collection.put(chunkId, new VectorEntry(chunkId, docId != null ? docId : "unknown", vector, content != null ? content : ""));
    }

    @Override
    public List<SearchResult> search(String kbId, float[] queryVector, int topK) {
        if (kbId == null || queryVector == null || topK <= 0) {
            return new ArrayList<>();
        }
        Map<String, VectorEntry> collection = collections.get(kbId);
        if (collection == null || collection.isEmpty()) {
            return new ArrayList<>();
        }
        return collection.values().stream()
                .map(entry -> {
                    double score = cosineSimilarity(queryVector, entry.vector);
                    return new SearchResult(entry.chunkId, score, entry.content, entry.docId, kbId);
                })
                .filter(r -> r.getScore() > 0)
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public int deleteByDocId(String kbId, String docId) {
        if (kbId == null || docId == null) {
            return 0;
        }
        Map<String, VectorEntry> collection = collections.get(kbId);
        if (collection == null) {
            return 0;
        }
        int removed = 0;
        for (Map.Entry<String, VectorEntry> entry : new ArrayList<>(collection.entrySet())) {
            if (docId.equals(entry.getValue().docId)) {
                collection.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }

    @Override
    public boolean dropCollection(String kbId) {
        if (kbId == null) {
            return false;
        }
        return collections.remove(kbId) != null;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
