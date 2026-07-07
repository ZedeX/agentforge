package com.agent.memory.outbox;

import com.agent.common.utils.JsonUtils;
import com.agent.memory.api.MemoryVectorStore;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.VectorInsertPayload;
import com.agent.memory.repository.MemoryRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MemoryVectorInsertOutboxConsumer} unit tests (S-04 Plan 10 extension).
 */
@DisplayName("MemoryVectorInsertOutboxConsumer S-04 Outbox consumer")
class MemoryVectorInsertOutboxConsumerTest {

    private MemoryRecordRepository repository;
    private MemoryVectorStore vectorStore;
    private MemoryVectorInsertOutboxConsumer consumer;

    @BeforeEach
    void setUp() {
        repository = mock(MemoryRecordRepository.class);
        vectorStore = mock(MemoryVectorStore.class);
        consumer = new MemoryVectorInsertOutboxConsumer(repository, vectorStore);
    }

    @Test
    @DisplayName("handle should insert vector when memory record exists")
    void should_InsertVector_When_RecordExists() {
        MemoryRecord record = new MemoryRecord("mem_001", MemoryType.SEMANTIC, "test content");
        record.setTenantId("t1");
        record.setStatus(MemoryStatus.ACTIVE);
        record.setImportanceScore(0.85);
        when(repository.findByMemoryId("mem_001")).thenReturn(Optional.of(record));

        VectorInsertPayload payload = new VectorInsertPayload("mem_001", "t1",
                new float[]{0.1f, 0.2f, 0.3f}, 3);
        String payloadJson = JsonUtils.toJson(payload);

        consumer.handle(MemoryVectorInsertOutboxConsumer.TOPIC, payloadJson);

        verify(vectorStore).insert(eq(record), any(EmbeddingVector.class));
    }

    @Test
    @DisplayName("handle should throw when memory record not found")
    void should_Throw_When_RecordNotFound() {
        when(repository.findByMemoryId("mem_missing")).thenReturn(Optional.empty());

        VectorInsertPayload payload = new VectorInsertPayload("mem_missing", "t1",
                new float[]{0.1f}, 1);
        String payloadJson = JsonUtils.toJson(payload);

        assertThatThrownBy(() -> consumer.handle(MemoryVectorInsertOutboxConsumer.TOPIC, payloadJson))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mem_missing");

        verify(vectorStore, never()).insert(any(), any());
    }

    @Test
    @DisplayName("handle should skip unexpected topic")
    void should_Skip_When_UnexpectedTopic() {
        consumer.handle("unexpected.topic", "{}");

        verify(vectorStore, never()).insert(any(), any());
        verify(repository, never()).findByMemoryId(any());
    }

    @Test
    @DisplayName("handle should propagate exception for outbox retry on vector store failure")
    void should_PropagateException_When_VectorStoreFails() {
        MemoryRecord record = new MemoryRecord("mem_fail", MemoryType.SEMANTIC, "fail content");
        record.setTenantId("t1");
        when(repository.findByMemoryId("mem_fail")).thenReturn(Optional.of(record));
        doThrow(new RuntimeException("Milvus down")).when(vectorStore).insert(any(MemoryRecord.class), any(EmbeddingVector.class));

        VectorInsertPayload payload = new VectorInsertPayload("mem_fail", "t1",
                new float[]{0.1f}, 1);
        String payloadJson = JsonUtils.toJson(payload);

        assertThatThrownBy(() -> consumer.handle(MemoryVectorInsertOutboxConsumer.TOPIC, payloadJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Milvus down");
    }
}
