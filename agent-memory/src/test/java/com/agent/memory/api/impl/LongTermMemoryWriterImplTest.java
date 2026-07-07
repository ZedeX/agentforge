package com.agent.memory.api.impl;

import com.agent.common.outbox.OutboxMessage;
import com.agent.common.outbox.OutboxRepository;
import com.agent.common.utils.JsonUtils;
import com.agent.memory.api.ImportanceScorer;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.VectorInsertPayload;
import com.agent.memory.repository.MemoryRecordRepository;
import com.agent.memory.scorer.ImportanceDimensions;
import com.agent.memory.scorer.ImportanceResult;
import com.agent.memory.scorer.ScoringContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LongTermMemoryWriterImpl} 单测。
 *
 * <p>覆盖 F12 骨架流程（旧 2 参构造器）+ T10 完整流程（4 参构造器）：
 * memoryId 生成、contentHash 计算、dedup 命中返回已有 ID、importance 评分、
 * DB 持久化、tenantId 为 null 时跳过 dedup、scorer/repository 为 null 时简化流程。
 */
@DisplayName("LongTermMemoryWriterImpl 长期记忆写入（F12 + T10）")
class LongTermMemoryWriterImplTest {

    // ===== F12 骨架（旧 2 参构造器，scorer/repository 为 null）=====

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

    @Test
    @DisplayName("F12 骨架：write 应自动计算 contentHash（SHA-256）")
    void should_ComputeContentHash_When_NotProvided_F12Skeleton() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding);
        MemoryRecord record = new MemoryRecord("mem_h", MemoryType.SEMANTIC, "测试 hash");

        writer.write(record);

        // SHA-256 输出 64 位 hex 字符串
        assertThat(record.getContentHash()).isNotNull().hasSize(64).matches("[0-9a-f]+");
    }

    // ===== T10 完整流程（4 参构造器，scorer + repository 可用）=====

    @Test
    @DisplayName("T10 全流程：write 应执行 dedup + score + save DB + insert vector")
    void should_RunFullPipeline_When_ScorerAndRepositoryProvided() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        ImportanceScorer scorer = mock(ImportanceScorer.class);
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        ImportanceResult result = new ImportanceResult(0.85, "HIGH", new ImportanceDimensions());
        when(scorer.score(any(MemoryRecord.class), any(ScoringContext.class))).thenReturn(result);
        when(repository.findByTenantIdAndContentHash(eq("t1"), any()))
                .thenReturn(Collections.emptyList());

        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding, scorer, repository);
        MemoryRecord record = new MemoryRecord("mem_full", MemoryType.SEMANTIC, "全流程测试");
        record.setTenantId("t1");

        String id = writer.write(record);

        assertThat(id).isEqualTo("mem_full");
        // 验证 dedup 检查被调用
        verify(repository).findByTenantIdAndContentHash(eq("t1"), any());
        // 验证 importance 评分被设置
        assertThat(record.getImportanceScore()).isEqualTo(0.85);
        assertThat(record.getImportanceLevel()).isEqualTo("HIGH");
        // 验证 DB save 被调用
        verify(repository).save(record);
        // 验证向量存储
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("T10 dedup：write 命中已有 contentHash 应返回已有 memoryId 且不重复写入")
    void should_DedupAndReturnExisting_When_ContentHashMatches() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        ImportanceScorer scorer = mock(ImportanceScorer.class);
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);

        // 模拟已存在记录
        MemoryRecord existing = new MemoryRecord("mem_existing", MemoryType.SEMANTIC, "重复内容");
        existing.setTenantId("t1");
        when(repository.findByTenantIdAndContentHash(eq("t1"), any()))
                .thenReturn(List.of(existing));

        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding, scorer, repository);
        MemoryRecord record = new MemoryRecord("mem_new", MemoryType.SEMANTIC, "重复内容");
        record.setTenantId("t1");

        String id = writer.write(record);

        // 返回已有 memoryId
        assertThat(id).isEqualTo("mem_existing");
        // 不应执行评分、DB save、向量写入
        verify(scorer, never()).score(any(), any());
        verify(repository, never()).save(any());
        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("T10：write 当 tenantId 为 null 时应跳过 dedup 检查")
    void should_SkipDedup_When_TenantIdIsNull() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        ImportanceScorer scorer = mock(ImportanceScorer.class);
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        when(scorer.score(any(), any())).thenReturn(new ImportanceResult(0.5, "MEDIUM", new ImportanceDimensions()));

        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding, scorer, repository);
        MemoryRecord record = new MemoryRecord("mem_no_tenant", MemoryType.SEMANTIC, "无租户");
        // tenantId 为 null（默认）

        String id = writer.write(record);

        assertThat(id).isEqualTo("mem_no_tenant");
        // 不应执行 dedup 检查
        verify(repository, never()).findByTenantIdAndContentHash(any(), any());
        // 但应执行 DB save（repository 非 null）
        verify(repository).save(record);
    }

    @Test
    @DisplayName("T10：write 当 scorer 为 null 时应跳过 importance 评分")
    void should_SkipScoring_When_ScorerIsNull() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        when(repository.findByTenantIdAndContentHash(any(), any())).thenReturn(Collections.emptyList());

        // scorer=null，repository 非 null
        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding, null, repository);
        MemoryRecord record = new MemoryRecord("mem_no_scorer", MemoryType.SEMANTIC, "无评分");
        record.setTenantId("t1");

        String id = writer.write(record);

        assertThat(id).isEqualTo("mem_no_scorer");
        // 仍应执行 dedup + save
        verify(repository).findByTenantIdAndContentHash(eq("t1"), any());
        verify(repository).save(record);
        // importance 应保持默认值 0.0
        assertThat(record.getImportanceScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("T10：write 应使用预设置的 contentHash 而不重新计算")
    void should_UsePresetContentHash_When_Provided() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        ImportanceScorer scorer = mock(ImportanceScorer.class);
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        when(repository.findByTenantIdAndContentHash(any(), any())).thenReturn(Collections.emptyList());
        when(scorer.score(any(), any())).thenReturn(new ImportanceResult(0.5, "MEDIUM", new ImportanceDimensions()));

        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding, scorer, repository);
        MemoryRecord record = new MemoryRecord("mem_preset", MemoryType.SEMANTIC, "内容");
        record.setTenantId("t1");
        String presetHash = "preset_hash_value_1234567890abcdef";
        record.setContentHash(presetHash);

        writer.write(record);

        // 应使用预设 hash 调用 dedup 检查
        verify(repository).findByTenantIdAndContentHash(eq("t1"), eq(presetHash));
        // contentHash 不应被覆盖
        assertThat(record.getContentHash()).isEqualTo(presetHash);
    }

    @Test
    @DisplayName("T10：write 对空 content 应计算空字符串 contentHash")
    void should_ComputeEmptyHash_When_ContentEmpty() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        ImportanceScorer scorer = mock(ImportanceScorer.class);
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        when(repository.findByTenantIdAndContentHash(any(), any())).thenReturn(Collections.emptyList());
        when(scorer.score(any(), any())).thenReturn(new ImportanceResult(0.5, "MEDIUM", new ImportanceDimensions()));

        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding, scorer, repository);
        MemoryRecord record = new MemoryRecord("mem_empty", MemoryType.SEMANTIC, "");
        record.setTenantId("t1");

        writer.write(record);

        // sha256("") 返回空字符串（实现中的边界处理）
        assertThat(record.getContentHash()).isEmpty();
    }

    @Test
    @DisplayName("T10：write 应将评分结果写入 record 的 importanceScore 和 importanceLevel")
    void should_PopulateImportance_When_ScorerReturnsResult() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        ImportanceScorer scorer = mock(ImportanceScorer.class);
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        ImportanceDimensions dims = new ImportanceDimensions();
        when(repository.findByTenantIdAndContentHash(any(), any())).thenReturn(Collections.emptyList());
        when(scorer.score(any(), any())).thenReturn(new ImportanceResult(0.42, "MEDIUM", dims));

        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding, scorer, repository);
        MemoryRecord record = new MemoryRecord("mem_score", MemoryType.SEMANTIC, "评分测试");
        record.setTenantId("t1");

        writer.write(record);

        assertThat(record.getImportanceScore()).isEqualTo(0.42);
        assertThat(record.getImportanceLevel()).isEqualTo("MEDIUM");
        verify(scorer).score(eq(record), any(ScoringContext.class));
    }

    @Test
    @DisplayName("T10：write 应调用 embeddingClient.embed 向量化内容")
    void should_CallEmbed_When_WriteExecuted() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        EmbeddingVector expectedVector = new EmbeddingVector(new float[]{0.1f, 0.2f});
        com.agent.memory.api.EmbeddingClient embedding = mock(com.agent.memory.api.EmbeddingClient.class);
        when(embedding.embed(any(String.class))).thenReturn(expectedVector);

        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding);
        MemoryRecord record = new MemoryRecord("mem_embed", MemoryType.SEMANTIC, "向量化内容");

        writer.write(record);

        verify(embedding).embed("向量化内容");
        assertThat(store.findVector("mem_embed")).isSameAs(expectedVector);
    }

    @Test
    @DisplayName("T10 skipWrite：应记录日志且不执行写入")
    void should_SkipWrite_When_Called() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding);

        writer.skipWrite("task_failed");

        assertThat(store.size()).isZero();
    }

    // ===== 辅助 =====

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }

    // ===== S-04 Outbox 扩展测试（5 参构造器，outboxRepository 可用）=====

    @Test
    @DisplayName("S-04 Outbox：write 应写入 outbox 消息而非直接 insert vector")
    void should_WriteToOutbox_When_OutboxRepositoryAvailable() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        ImportanceScorer scorer = mock(ImportanceScorer.class);
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        OutboxRepository outboxRepository = mock(OutboxRepository.class);
        when(scorer.score(any(), any())).thenReturn(new ImportanceResult(0.7, "HIGH", new ImportanceDimensions()));
        when(repository.findByTenantIdAndContentHash(any(), any())).thenReturn(Collections.emptyList());

        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(
                store, embedding, scorer, repository, outboxRepository);
        MemoryRecord record = new MemoryRecord("mem_outbox", MemoryType.SEMANTIC, "outbox test");
        record.setTenantId("t1");

        String id = writer.write(record);

        assertThat(id).isEqualTo("mem_outbox");
        // Outbox message should be saved
        verify(outboxRepository).save(any(OutboxMessage.class));
        // Vector store should NOT be called directly (outbox will handle it later)
        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("S-04 Outbox：write 写入的 outbox 消息应包含正确的 topic 和 payload")
    void should_WriteCorrectOutboxMessage_When_OutboxRepositoryAvailable() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        OutboxRepository outboxRepository = mock(OutboxRepository.class);
        when(repository.findByTenantIdAndContentHash(any(), any())).thenReturn(Collections.emptyList());

        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(
                store, embedding, null, repository, outboxRepository);
        MemoryRecord record = new MemoryRecord("mem_payload", MemoryType.SEMANTIC, "payload test");
        record.setTenantId("t1");

        writer.write(record);

        // Capture the OutboxMessage to verify its content
        verify(outboxRepository).save(argThat(msg -> {
            if (msg == null) return false;
            if (!LongTermMemoryWriterImpl.OUTBOX_TOPIC_VECTOR_INSERT.equals(msg.getTopic())) return false;
            if (!"mem_payload".equals(msg.getAggregateId())) return false;
            // Verify payload is valid JSON with memoryId
            try {
                VectorInsertPayload payload = JsonUtils.fromJson(msg.getPayload(), VectorInsertPayload.class);
                return "mem_payload".equals(payload.getMemoryId())
                        && "t1".equals(payload.getTenantId())
                        && payload.getEmbeddingDim() == 1024;
            } catch (Exception e) {
                return false;
            }
        }));
    }

    @Test
    @DisplayName("S-04 Outbox：write 当 outbox 写入失败时应降级为直接 insert")
    void should_FallBackToDirectInsert_When_OutboxWriteFails() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        OutboxRepository outboxRepository = mock(OutboxRepository.class);
        when(repository.findByTenantIdAndContentHash(any(), any())).thenReturn(Collections.emptyList());
        when(outboxRepository.save(any())).thenThrow(new RuntimeException("DB connection lost"));

        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(
                store, embedding, null, repository, outboxRepository);
        MemoryRecord record = new MemoryRecord("mem_fallback", MemoryType.SEMANTIC, "fallback test");
        record.setTenantId("t1");

        String id = writer.write(record);

        assertThat(id).isEqualTo("mem_fallback");
        // Should fall back to direct vector insert
        assertThat(store.size()).isEqualTo(1);
        assertThat(store.find("mem_fallback")).isSameAs(record);
    }

    @Test
    @DisplayName("S-04 Outbox：F12 骨架（2 参构造器）仍走直接 insert 路径")
    void should_UseDirectInsert_When_NoOutboxRepository() {
        MemoryVectorStoreImpl store = new MemoryVectorStoreImpl();
        MockEmbeddingClientImpl embedding = new MockEmbeddingClientImpl();
        LongTermMemoryWriterImpl writer = new LongTermMemoryWriterImpl(store, embedding);

        MemoryRecord record = new MemoryRecord("mem_direct", MemoryType.SEMANTIC, "direct insert");
        writer.write(record);

        // Should directly insert into vector store
        assertThat(store.size()).isEqualTo(1);
    }
}
