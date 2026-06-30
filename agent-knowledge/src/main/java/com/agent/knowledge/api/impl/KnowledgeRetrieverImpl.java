package com.agent.knowledge.api.impl;

import com.agent.knowledge.api.KnowledgeRetriever;
import com.agent.knowledge.model.KnowledgeQuery;
import com.agent.knowledge.model.SearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory knowledge retriever (doc 07-knowledge §7.1).
 *
 * <p>Skeleton stage: keyword-based substring matching. Score = count of query term occurrences
 * in chunk content. Milvus + MMR reranking deferred to Plan 08 T10.</p>
 */
@Component
public class KnowledgeRetrieverImpl implements KnowledgeRetriever {

    private static class IndexedChunk {
        final String chunkId;
        final String docId;
        final String content;
        IndexedChunk(String chunkId, String docId, String content) {
            this.chunkId = chunkId;
            this.docId = docId;
            this.content = content;
        }
    }

    private final Map<String, Map<String, IndexedChunk>> index = new ConcurrentHashMap<>();

    @Override
    public List<SearchResult> search(KnowledgeQuery query) {
        if (query == null || query.getKbId() == null || query.getQuery() == null) {
            return new ArrayList<>();
        }
        Map<String, IndexedChunk> kbIndex = index.get(query.getKbId());
        if (kbIndex == null || kbIndex.isEmpty()) {
            return new ArrayList<>();
        }
        String queryText = query.getQuery().toLowerCase();
        String[] terms = queryText.split("\\s+");
        int topK = query.getTopK() > 0 ? query.getTopK() : 5;
        return kbIndex.values().stream()
                .map(chunk -> {
                    double score = computeScore(chunk.content, terms);
                    return new SearchResult(chunk.chunkId, score, chunk.content, chunk.docId, query.getKbId());
                })
                .filter(r -> r.getScore() > 0)
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private double computeScore(String content, String[] terms) {
        if (content == null || content.isEmpty()) {
            return 0.0;
        }
        String lower = content.toLowerCase();
        double score = 0.0;
        for (String term : terms) {
            if (term.isEmpty()) {
                continue;
            }
            int count = 0;
            int idx = 0;
            while ((idx = lower.indexOf(term, idx)) != -1) {
                count++;
                idx += term.length();
            }
            score += count;
        }
        return score;
    }

    @Override
    public void indexChunk(String kbId, String chunkId, String docId, String content) {
        if (kbId == null || chunkId == null || content == null) {
            return;
        }
        index.computeIfAbsent(kbId, k -> new ConcurrentHashMap<>())
                .put(chunkId, new IndexedChunk(chunkId, docId != null ? docId : "unknown", content));
    }

    @Override
    public int removeByDocId(String kbId, String docId) {
        if (kbId == null || docId == null) {
            return 0;
        }
        Map<String, IndexedChunk> kbIndex = index.get(kbId);
        if (kbIndex == null) {
            return 0;
        }
        int removed = 0;
        for (Map.Entry<String, IndexedChunk> entry : new ArrayList<>(kbIndex.entrySet())) {
            if (docId.equals(entry.getValue().docId)) {
                kbIndex.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }
}
