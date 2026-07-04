package com.agent.memory.api.impl;

import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.MemoryRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LongTermMemoryWriterImplTest {

    @Test
    @DisplayName("write 应向量化并写入向量存储，返回 memoryId")
    void should_WriteAndReturnId_When_RecordProvided() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding);
        MemoryRecord record = new MemoryRecord("mem_001", MemoryType.SEMANTIC, "长期记忆内容");

        String id = writer.write(record);

        assertThat(id).isEqualTo("mem_001");
        assertThat(store.size()).isEqualTo(1);
        assertThat(store.find("mem_001")).isSameAs(record);
        assertThat(store.findVector("mem_001")).isNotNull();
        assertThat(store.findVector("mem_001").getDim()).isEqualTo(1024);
    }

    @Test
    @DisplayName("write 对空 memoryId 应自动生成 UUID 并写入")
    void should_AutoGenerateId_When_MemoryIdEmpty() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding);
        MemoryRecord record = new MemoryRecord(null, MemoryType.EPISODIC, "无ID记忆");

        String id = writer.write(record);

        assertThat(id).isNotNull().isNotEmpty();
        assertThat(record.getMemoryId()).isEqualTo(id);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("write 对 null 记录应返回 null 且不写入")
    void should_ReturnNull_When_RecordIsNull() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding);

        String id = writer.write(null);

        assertThat(id).isNull();
        assertThat(store.size()).isZero();
    }
}