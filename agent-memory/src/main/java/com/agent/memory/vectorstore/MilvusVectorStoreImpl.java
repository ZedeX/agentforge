package com.agent.memory.vectorstore;

import com.agent.memory.api.MemoryVectorStore;
import com.agent.memory.config.MemoryProperties;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.MemorySearchHit;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Milvus-backed implementation of {@link MemoryVectorStore} (Plan 03 T6).
 *
 * <p>Active when {@code memory.milvus.enabled=true}. Delegates to {@link MilvusClientV2}
 * for insert/search/delete operations against the {@code agent_memory_vector} collection.</p>
 *
 * <p>Schema aligned with doc 04-memory §7.3, index with doc 04 §7.4 (IVF_FLAT, COSINE).</p>
 *
 * @see MemoryVectorStore
 * @see com.agent.memory.api.impl.MemoryVectorStoreImpl
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "memory.milvus.enabled", havingValue = "true")
public class MilvusVectorStoreImpl implements MemoryVectorStore {

    private final MilvusClientV2 milvusClient;
    private final String collectionName;
    private volatile boolean collectionInitialized = false;

    public MilvusVectorStoreImpl(MilvusClientV2 milvusClient, MemoryProperties properties) {
        this.milvusClient = milvusClient;
        this.collectionName = properties.getMilvus().getCollection();
    }

    @Override
    public void insert(MemoryRecord record, EmbeddingVector vector) {
        if (record == null || record.getMemoryId() == null || record.getMemoryId().isEmpty()) {
            log.warn("Insert skipped: record or memoryId is null/empty");
            return;
        }
        ensureCollectionExists();

        JSONObject data = new JSONObject();
        data.put(MilvusSchemaBuilder.FIELD_ID, record.getMemoryId());
        data.put(MilvusSchemaBuilder.FIELD_TENANT_ID, record.getTenantId());
        data.put(MilvusSchemaBuilder.FIELD_VECTOR,
                vector != null && vector.getValues() != null
                        ? toFloatList(vector.getValues())
                        : zeroVector());
        data.put(MilvusSchemaBuilder.FIELD_IMPORTANCE,
                (float) record.getImportanceScore());
        data.put(MilvusSchemaBuilder.FIELD_STATUS,
                record.getStatus() != null ? record.getStatus().name() : MemoryStatus.ACTIVE.name());
        data.put(MilvusSchemaBuilder.FIELD_CREATED_AT,
                record.getCreatedAt() != null ? record.getCreatedAt().toEpochMilli() : Instant.now().toEpochMilli());

        InsertReq insertReq = InsertReq.builder()
                .collectionName(collectionName)
                .data(List.of(data))
                .build();

        milvusClient.insert(insertReq);
        log.debug("Inserted vector into Milvus: memoryId={}", record.getMemoryId());
    }

    @Override
    public List<MemorySearchHit> search(float[] queryVector, String tenantId, int topK,
                                          double scoreThreshold, MemoryStatus... statuses) {
        if (queryVector == null || queryVector.length == 0 || topK <= 0) {
            return List.of();
        }
        ensureCollectionExists();

        String filter = buildFilter(tenantId, statuses);

        SearchReq searchReq = SearchReq.builder()
                .collectionName(collectionName)
                .data(List.of(toFloatList(queryVector)))
                .topK(topK)
                .filter(filter)
                .outputFields(List.of(
                        MilvusSchemaBuilder.FIELD_ID,
                        MilvusSchemaBuilder.FIELD_TENANT_ID,
                        MilvusSchemaBuilder.FIELD_IMPORTANCE,
                        MilvusSchemaBuilder.FIELD_STATUS,
                        MilvusSchemaBuilder.FIELD_CREATED_AT))
                .consistencyLevel(ConsistencyLevel.STRONG)
                .build();

        SearchResp searchResp = milvusClient.search(searchReq);
        List<SearchResp.SearchResult> results = searchResp.getSearchResults().get(0);

        return results.stream()
                .filter(r -> r.getDistance() != null && r.getDistance() >= scoreThreshold)
                .map(r -> {
                    MemoryRecord mr = new MemoryRecord();
                    mr.setMemoryId(getStringField(r, MilvusSchemaBuilder.FIELD_ID));
                    mr.setTenantId(getStringField(r, MilvusSchemaBuilder.FIELD_TENANT_ID));
                    Float importance = getFloatField(r, MilvusSchemaBuilder.FIELD_IMPORTANCE);
                    mr.setImportanceScore(importance != null ? importance.doubleValue() : null);
                    String statusStr = getStringField(r, MilvusSchemaBuilder.FIELD_STATUS);
                    try {
                        mr.setStatus(MemoryStatus.valueOf(statusStr));
                    } catch (IllegalArgumentException e) {
                        mr.setStatus(MemoryStatus.ACTIVE);
                    }
                    Long createdAtMs = getLongField(r, MilvusSchemaBuilder.FIELD_CREATED_AT);
                    mr.setCreatedAt(createdAtMs != null ? Instant.ofEpochMilli(createdAtMs) : null);
                    float score = r.getDistance() != null ? r.getDistance() : 0.0f;
                    return new MemorySearchHit(mr, score);
                })
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String memoryId) {
        if (memoryId == null || memoryId.isEmpty()) {
            return;
        }
        ensureCollectionExists();

        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName)
                .filter(MilvusSchemaBuilder.FIELD_ID + " == \"" + memoryId + "\"")
                .build();

        milvusClient.delete(deleteReq);
        log.debug("Deleted vector from Milvus: memoryId={}", memoryId);
    }

    // ---- Internal helpers ----

    private void ensureCollectionExists() {
        if (collectionInitialized) {
            return;
        }
        synchronized (this) {
            if (collectionInitialized) {
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
                CreateCollectionReq createReq = MilvusSchemaBuilder.buildSchema(collectionName);
                milvusClient.createCollection(createReq);
                log.info("Milvus collection '{}' created successfully", collectionName);
            }
            collectionInitialized = true;
        }
    }

    private String buildFilter(String tenantId, MemoryStatus... statuses) {
        List<String> conditions = new ArrayList<>();
        if (tenantId != null && !tenantId.isEmpty()) {
            conditions.add(MilvusSchemaBuilder.FIELD_TENANT_ID + " == \"" + tenantId + "\"");
        }
        if (statuses != null && statuses.length > 0) {
            String statusList = Arrays.stream(statuses)
                    .map(s -> "\"" + s.name() + "\"")
                    .collect(Collectors.joining(", "));
            conditions.add(MilvusSchemaBuilder.FIELD_STATUS + " in [" + statusList + "]");
        }
        return conditions.isEmpty() ? "" : String.join(" and ", conditions);
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }

    private static List<Float> zeroVector() {
        List<Float> zeros = new ArrayList<>(MilvusSchemaBuilder.VECTOR_DIM);
        for (int i = 0; i < MilvusSchemaBuilder.VECTOR_DIM; i++) {
            zeros.add(0.0f);
        }
        return zeros;
    }

    private static String getStringField(SearchResp.SearchResult result, String fieldName) {
        Object val = result.getEntity().get(fieldName);
        return val != null ? val.toString() : null;
    }

    private static Float getFloatField(SearchResp.SearchResult result, String fieldName) {
        Object val = result.getEntity().get(fieldName);
        if (val instanceof Number) {
            return ((Number) val).floatValue();
        }
        return null;
    }

    private static Long getLongField(SearchResp.SearchResult result, String fieldName) {
        Object val = result.getEntity().get(fieldName);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return null;
    }
}
