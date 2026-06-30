package com.agent.memory.api.impl;

import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryVectorStoreImplTest {

    @Test
    @DisplayName("insert 应能将 MemoryRecord 与 EmbeddingVector 存入内部存储")
    void should_StoreRecordAndVector_When_InsertInvoked() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MemoryRecord record = new MemoryRecord("mem_001", MemoryType.SEMANTIC, "测试内容");
        EmbeddingVector vector = new EmbeddingVector(new float[]{0.1f, 0.2f, 0.3f});

        store.insert(record, vector);

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.find("mem_001")).isSameAs(record);
        assertThat(store.findVector("mem_001")).isSameAs(vector);
    }

    @Test
    @DisplayName("insert 对 null 或空 memoryId 应跳过写入并保持存储为空")
    void should_SkipInsert_When_MemoryIdIsNullOrEmpty() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        EmbeddingVector vector = new EmbeddingVector(new float[]{0.1f});

        store.insert(null, vector);
        store.insert(new MemoryRecord(null, MemoryType.EPISODIC, "无ID"), vector);

        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("多次 insert 同一 memoryId 应覆盖旧记录")
    void should_OverwriteRecord_When_InsertSameIdTwice() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MemoryRecord r1 = new MemoryRecord("mem_dup", MemoryType.SEMANTIC, "v1");
        MemoryRecord r2 = new MemoryRecord("mem_dup", MemoryType.SEMANTIC, "v2");
        EmbeddingVector v1 = new EmbeddingVector(new float[]{1.0f});

        store.insert(r1, v1);
        store.insert(r2, v1);

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.find("mem_dup").getContent()).isEqualTo("v2");
    }
}