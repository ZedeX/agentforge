package com.agent.knowledge.milvus;

import com.agent.knowledge.api.VectorStore;
import com.agent.knowledge.config.KnowledgeProperties;
import com.agent.knowledge.model.SearchResult;
import com.alibaba.fastjson.JSONObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milvus-backed implementation of {@link VectorStore} (Plan 08 T10).
 *
 * <p>Active when {@code knowledge.milvus.enabled=true}. Delegates to {@link MilvusClientV2}
 * for insert/search/delete operations against per-KB collections ({@code kb_{kbId}}).</p>
 *
 * <p>Schema aligned with doc 07-knowledge §6.1, index with IVF_FLAT COSINE.</p>
 *
 * @see VectorStore
 * @see com.agent.knowledge.api.impl.VectorStoreImpl
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "knowledge.milvus.enabled", havingValue = "true")
public class MilvusVectorStoreImpl implements VectorStore {

    private final MilvusClientV2 milvusClient;
    private final Set<String> initializedCollections = ConcurrentHashMap.newKeySet();

    public MilvusVectorStoreImpl(MilvusClientV2 milvusClient, KnowledgeProperties properties) {
        this.milvusClient = milvusClient;
    }

    @Override
    public void ensureCollection(String kbId, int dimension) {
        if (kbId == null || kbId.isEmpty()) {
            return;
        }
        String collectionName = collectionName(kbId);
        if (initializedCollections.contains(collectionName)) {
            return;
        }
        synchronized (this) {
            if (initializedCollections.contains(collectionName)) {
                return;
            }
            try {
                GetCollectionStatsReq statsReq = GetCollectionStatsReq.builder()
                        .collectionName(collectionName)
                        .build();
                milvusClient.getCollectionStats(statsReq);
                log.info("Milvus collection '{}' already exists", collectionName);
            } catch (Exception e) {
                log.info("Creating Milvus collection '{}'...", collectionName);
                CreateCollectionReq createReq = KnowledgeSchemaBuilder.buildSchema(collectionName);
                milvusClient.createCollection(createReq);
                log.info("Milvus collection '{}' created successfully", collectionName);
            }
            initializedCollections.add(collectionName);
        }
    }

    @Override
    public void upsert(String kbId, String chunkId, float[] vector, String content, String docId) {
        if (kbId == null || chunkId == null || vector == null) {
            return;
        }
        ensureCollection(kbId, vector.length);

        JSONObject data = new JSONObject();
        data.put(KnowledgeSchemaBuilder.FIELD_CHUNK_ID, chunkId);
        data.put(KnowledgeSchemaBuilder.FIELD_DOC_ID, docId != null ? docId : "unknown");
        data.put(KnowledgeSchemaBuilder.FIELD_VECTOR, toFloatList(vector));
        data.put(KnowledgeSchemaBuilder.FIELD_CONTENT, content != null ? content : "");

        InsertReq insertReq = InsertReq.builder()
                .collectionName(collectionName(kbId))
                .data(List.of(data))
                .build();

        milvusClient.insert(insertReq);
        log.debug("Upserted vector into Milvus: kbId={} chunkId={}", kbId, chunkId);
    }

    @Override
    public List<SearchResult> search(String kbId, float[] queryVector, int topK) {
        if (kbId == null || queryVector == null || queryVector.length == 0 || topK <= 0) {
            return new ArrayList<>();
        }
        ensureCollection(kbId, queryVector.length);

        SearchReq searchReq = SearchReq.builder()
                .collectionName(collectionName(kbId))
                .data(List.of(toFloatList(queryVector)))
                .topK(topK)
                .outputFields(List.of(
                        KnowledgeSchemaBuilder.FIELD_CHUNK_ID,
                        KnowledgeSchemaBuilder.FIELD_DOC_ID,
                        KnowledgeSchemaBuilder.FIELD_CONTENT))
                .consistencyLevel(ConsistencyLevel.STRONG)
                .build();

        SearchResp searchResp = milvusClient.search(searchReq);
        List<SearchResp.SearchResult> results = searchResp.getSearchResults().get(0);

        List<SearchResult> searchResults = new ArrayList<>();
        for (SearchResp.SearchResult r : results) {
            String chunkId = getStringField(r, KnowledgeSchemaBuilder.FIELD_CHUNK_ID);
            String docId = getStringField(r, KnowledgeSchemaBuilder.FIELD_DOC_ID);
            String content = getStringField(r, KnowledgeSchemaBuilder.FIELD_CONTENT);
            float score = r.getDistance() != null ? r.getDistance() : 0.0f;
            searchResults.add(new SearchResult(chunkId, score, content, docId, kbId));
        }
        return searchResults;
    }

    @Override
    public int deleteByDocId(String kbId, String docId) {
        if (kbId == null || docId == null) {
            return 0;
        }
        ensureCollection(kbId, KnowledgeSchemaBuilder.VECTOR_DIM);

        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName(kbId))
                .filter(KnowledgeSchemaBuilder.FIELD_DOC_ID + " == \"" + docId + "\"")
                .build();

        milvusClient.delete(deleteReq);
        log.debug("Deleted vectors from Milvus: kbId={} docId={}", kbId, docId);
        return 0;
    }

    @Override
    public boolean dropCollection(String kbId) {
        if (kbId == null) {
            return false;
        }
        String collectionName = collectionName(kbId);
        try {
            milvusClient.dropCollection(io.milvus.v2.service.collection.request.DropCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
            initializedCollections.remove(collectionName);
            log.info("Dropped Milvus collection '{}'", collectionName);
            return true;
        } catch (Exception e) {
            log.warn("Failed to drop Milvus collection '{}': {}", collectionName, e.getMessage());
            return false;
        }
    }

    private static String collectionName(String kbId) {
        return "kb_" + kbId;
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }

    private static String getStringField(SearchResp.SearchResult result, String fieldName) {
        Map<String, Object> entity = result.getEntity();
        if (entity == null) {
            return null;
        }
        Object val = entity.get(fieldName);
        return val != null ? val.toString() : null;
    }
}
