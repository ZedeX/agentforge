package com.agent.memory.api.impl;

import com.agent.memory.api.MemoryVectorStore;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆向量存储实现（F12.D5: insert vector into Milvus）。
 *
 * <p>骨架阶段的内存 Mock 实现：使用 {@link ConcurrentHashMap} 模拟 Milvus 向量库，
 * 按 memoryId 索引 MemoryRecord + EmbeddingVector。后续 P7-4 接入真实 Milvus SDK 时，
 * 替换为 MilvusClient 调用即可。</p>
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