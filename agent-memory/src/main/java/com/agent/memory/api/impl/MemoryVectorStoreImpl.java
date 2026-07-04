package com.agent.memory.api.impl;

import com.agent.memory.api.MemoryVectorStore;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.MemorySearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 记忆向量存储实现（F12.D5: insert vector into Milvus + Plan 03 T10 search/delete）。
 *
 * <p>骨架阶段的内存 Mock 实现：使用 {@link ConcurrentHashMap} 模拟 Milvus 向量库，
 * 按 memoryId 索引 MemoryRecord + EmbeddingVector。T10 新增 search（余弦相似度 topK 检索）
 * + delete（按 memoryId 删除）。后续 T6 接入真实 Milvus SDK 时，替换为 MilvusClient 调用即可。</p>
 *
 * <p>search 余弦相似度计算：dot(query, entry) / (|query| × |entry|)，范围 [-1, 1]，
 * 截断到 [0, 1]（负相关视为不相似）。</p>
 *
 * @see MemoryVectorStore
 */
@Component
public class MemoryVectorStoreImpl implements MemoryVectorStore {

    private static final Logger log = LoggerFactory.getLogger(MemoryVectorStoreImpl.class);

    /** 内存向量存储，key = memoryId，value = 记录 + 向量的条目。 */
    private final Map<String, StoreEntry> store = new ConcurrentHashMap<>();

    @Override
    public void insert(MemoryRecord record, EmbeddingVector vector) {
        if (record == null) {
            log.warn("插入向量存储失败：MemoryRecord 为 null");
            return;
        }
        String id = record.getMemoryId();
        if (id == null || id.isEmpty()) {
            log.warn("插入向量存储失败：memoryId 为空");
            return;
        }
        store.put(id, new StoreEntry(record, vector));
        log.debug("已插入记忆向量 memoryId={} dim={}", id,
                vector == null ? 0 : vector.getDim());
    }

    /**
     * T10: 向量检索（余弦相似度 topK）。
     *
     * <p>流程：遍历 store → 过滤 tenantId + statuses → 计算余弦相似度 → 过滤 scoreThreshold
     * → 按 score 降序 → 取 topK。</p>
     */
    @Override
    public List<MemorySearchHit> search(float[] queryVector, String tenantId, int topK,
                                         double scoreThreshold, MemoryStatus... statuses) {
        if (queryVector == null || queryVector.length == 0 || topK <= 0) {
            return List.of();
        }
        Set<MemoryStatus> statusFilter = statuses == null || statuses.length == 0
                ? null
                : Arrays.stream(statuses).collect(Collectors.toSet());

        List<MemorySearchHit> hits = new ArrayList<>();
        for (StoreEntry entry : store.values()) {
            MemoryRecord record = entry.record;
            // tenant 过滤
            if (tenantId != null && !tenantId.equals(record.getTenantId())) {
                continue;
            }
            // status 过滤
            if (statusFilter != null && !statusFilter.contains(record.getStatus())) {
                continue;
            }
            // 向量为空跳过
            if (entry.vector == null || entry.vector.getValues() == null) {
                continue;
            }
            double score = cosineSimilarity(queryVector, entry.vector.getValues());
            // 截断到 [0, 1]
            score = Math.max(0.0, Math.min(1.0, score));
            if (score < scoreThreshold) {
                continue;
            }
            hits.add(new MemorySearchHit(record, score));
        }
        // 按 score 降序，取 topK
        hits.sort(Comparator.comparingDouble(MemorySearchHit::getScore).reversed());
        if (hits.size() > topK) {
            hits = hits.subList(0, topK);
        }
        log.debug("向量检索完成 tenantId={} topK={} threshold={} hits={}",
                tenantId, topK, scoreThreshold, hits.size());
        return hits;
    }

    /**
     * T10: 删除指定 memoryId 的向量。
     */
    @Override
    public void delete(String memoryId) {
        if (memoryId == null || memoryId.isEmpty()) {
            return;
        }
        StoreEntry removed = store.remove(memoryId);
        if (removed != null) {
            log.debug("已删除记忆向量 memoryId={}", memoryId);
        }
    }

    /** 余弦相似度：dot(a, b) / (|a| × |b|)。 */
    private static double cosineSimilarity(float[] a, float[] b) {
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
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** 测试辅助：返回当前存储的记忆条数。 */
    public int size() {
        return store.size();
    }

    /** 测试辅助：按 memoryId 查询已存储的记录。 */
    public MemoryRecord find(String memoryId) {
        StoreEntry e = store.get(memoryId);
        return e == null ? null : e.record;
    }

    /** 测试辅助：按 memoryId 查询已存储的向量。 */
    public EmbeddingVector findVector(String memoryId) {
        StoreEntry e = store.get(memoryId);
        return e == null ? null : e.vector;
    }

    /** 内部存储条目，成对持有记录与向量。 */
    private static final class StoreEntry {
        final MemoryRecord record;
        final EmbeddingVector vector;

        StoreEntry(MemoryRecord record, EmbeddingVector vector) {
            this.record = record;
            this.vector = vector;
        }
    }
}
