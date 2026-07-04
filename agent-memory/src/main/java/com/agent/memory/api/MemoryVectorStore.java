package com.agent.memory.api;

import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.MemorySearchHit;

import java.util.List;

/**
 * Memory vector store port (F12.D5: insert vector into Milvus + Plan 03 T10 search/delete).
 *
 * <p>三种能力：</p>
 * <ul>
 *   <li>{@link #insert} — 写入记忆 + 向量（F12.D5 骨架）</li>
 *   <li>{@link #search} — T10 Recall RPC 向量检索（topK + scoreThreshold + tenant/status 过滤）</li>
 *   <li>{@link #delete} — T10 删除指定 memoryId 的向量（TTL 物理删除 / dedup 合并后删旧）</li>
 * </ul>
 */
public interface MemoryVectorStore {

    /**
     * Insert a memory record + its embedding vector into the vector store.
     */
    void insert(MemoryRecord record, EmbeddingVector vector);

    /**
     * T10: 向量检索（Recall RPC 用）。
     *
     * @param queryVector    查询向量（1024 维）
     * @param tenantId       租户隔离过滤
     * @param topK           返回最多 topK 条
     * @param scoreThreshold 余弦相似度阈值，低于此值过滤（默认 0.75，对齐 doc 04 §8.2）
     * @param statuses       状态过滤（仅返回这些状态的记录，空表示不限）
     * @return 命中列表，按 score 降序
     */
    List<MemorySearchHit> search(float[] queryVector, String tenantId, int topK,
                                  double scoreThreshold, MemoryStatus... statuses);

    /**
     * T10: 删除指定 memoryId 的向量。
     *
     * @param memoryId 记忆业务 ID
     */
    void delete(String memoryId);
}
