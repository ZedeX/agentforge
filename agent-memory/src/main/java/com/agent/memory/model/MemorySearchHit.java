package com.agent.memory.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 向量检索命中项（Plan 03 T10 Recall RPC 用）。
 *
 * <p>持有命中的 {@link MemoryRecord} + 余弦相似度 score（[0, 1]）。
 * 由 {@link com.agent.memory.api.MemoryVectorStore#search} 返回，
 * 供 gRPC Recall RPC 综合排序（score × (0.5 + 0.5 × importance)）后映射为 proto RecalledMemory。</p>
 */
@Getter
@Setter
@AllArgsConstructor
public class MemorySearchHit {

    /** 命中的记忆记录。 */
    private MemoryRecord record;

    /** 余弦相似度分数 [0.0, 1.0]。 */
    private double score;
}
