package com.agent.memory.grpc;

import agentplatform.memory.v1.DistillAck;
import agentplatform.memory.v1.DistillRequest;
import agentplatform.memory.v1.GetMemoryByIdRequest;
import agentplatform.memory.v1.MemoryRecord;
import agentplatform.memory.v1.RecallRequest;
import agentplatform.memory.v1.RecallResponse;
import agentplatform.memory.v1.WriteAck;
import agentplatform.memory.v1.WriteLongTermRequest;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.memory.api.EmbeddingClient;
import com.agent.memory.api.LongTermMemoryWriter;
import com.agent.memory.api.MemoryDistiller;
import com.agent.memory.api.MemoryVectorStore;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.MemorySearchHit;
import com.agent.memory.repository.MemoryRecordRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MemoryServiceGrpcImpl} 单测（Plan 03 T10，覆盖 4 RPC 正常流 + 异常流）。
 *
 * <p>纯单测：mock {@link LongTermMemoryWriter} / {@link EmbeddingClient} /
 * {@link MemoryVectorStore} / {@link MemoryDistiller} / {@link MemoryRecordRepository}，
 * 使用真实 {@link MemoryRecordMapper} + 真实 {@link GrpcExceptionAdvice}，
 * 用 capturing StreamObserver 捕获 onNext/onError。</p>
 *
 * <p>验证场景：</p>
 * <ul>
 *   <li>WriteLongTerm 正常流 → onNext + onCompleted，验证 memory_id</li>
 *   <li>WriteLongTerm 空 content → onError INVALID_ARGUMENT</li>
 *   <li>Recall 正常流 → 返回 topK 综合排序结果</li>
 *   <li>Recall 跨租户不返回（验证 vectorStore.search 用 tenantId 过滤）</li>
 *   <li>Recall 查无结果 → 返回空列表（非错误）</li>
 *   <li>TriggerDistill 正常流 → 返回 DistillAck</li>
 *   <li>GetMemoryById 正常流 → 返回 proto MemoryRecord</li>
 *   <li>GetMemoryById 不存在 → onError NOT_FOUND</li>
 * </ul>
 */
@DisplayName("MemoryServiceGrpcImpl gRPC 服务（Plan 03 T10）")
class MemoryServiceGrpcImplTest {

    private LongTermMemoryWriter writer;
    private EmbeddingClient embeddingClient;
    private MemoryVectorStore vectorStore;
    private MemoryDistiller distiller;
    private MemoryRecordRepository repository;
    private MemoryServiceGrpcImpl grpcService;

    @BeforeEach
    void setUp() {
        writer = mock(LongTermMemoryWriter.class);
        embeddingClient = mock(EmbeddingClient.class);
        vectorStore = mock(MemoryVectorStore.class);
        distiller = mock(MemoryDistiller.class);
        repository = mock(MemoryRecordRepository.class);
        MemoryRecordMapper mapper = new MemoryRecordMapper();
        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();
        grpcService = new MemoryServiceGrpcImpl(
                writer, embeddingClient, vectorStore, distiller, repository, mapper, advice);
    }

    // ===== RPC 1: WriteLongTerm =====

    @Test
    @DisplayName("Should_WriteLongTerm_When_ContentValid: 正常写入 → onNext + onCompleted，验证 memory_id")
    void should_WriteLongTerm_When_ContentValid() {
        // given
        WriteLongTermRequest req = WriteLongTermRequest.newBuilder()
                .setAgentId(1001L)
                .setUserId("user-001")
                .setDomain("coding")
                .setMemoryType("semantic")
                .setContent("Java 17 record 类是不可变数据载体")
                .addTags("java")
                .addTags("programming")
                .setSourceTaskId("task-001")
                .build();
        when(writer.write(any(com.agent.memory.model.MemoryRecord.class)))
                .thenReturn("mem-uuid-001");

        // when
        CapturingObserver<WriteAck> observer = new CapturingObserver<>();
        grpcService.writeLongTerm(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        assertThat(observer.values).hasSize(1);
        WriteAck ack = observer.values.get(0);
        assertThat(ack.getMemoryId()).isEqualTo("mem-uuid-001");
        assertThat(ack.getDeduplicated()).isFalse();
        verify(writer).write(any(com.agent.memory.model.MemoryRecord.class));
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_ContentEmpty: 空 content → onError INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_ContentEmpty() {
        // given
        WriteLongTermRequest req = WriteLongTermRequest.newBuilder()
                .setAgentId(1001L)
                .setMemoryType("semantic")
                .setContent("")
                .build();

        // when
        CapturingObserver<WriteAck> observer = new CapturingObserver<>();
        grpcService.writeLongTerm(req, observer);

        // then
        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(writer, never()).write(any());
    }

    // ===== RPC 2: Recall =====

    @Test
    @DisplayName("Should_Recall_When_QueryValid: 正常召回 → 返回 topK 综合排序结果")
    void should_Recall_When_QueryValid() {
        // given
        RecallRequest req = RecallRequest.newBuilder()
                .setAgentId(1001L)
                .setQuery("Java 编程")
                .setTopK(5)
                .build();
        float[] queryVec = new float[1024];
        Arrays.fill(queryVec, 0.1f);
        when(embeddingClient.embed("Java 编程", "1001")).thenReturn(queryVec);

        com.agent.memory.model.MemoryRecord r1 = buildRecord("mem-1", "1001",
                "Java 17 新特性", 0.8, MemoryStatus.ACTIVE);
        com.agent.memory.model.MemoryRecord r2 = buildRecord("mem-2", "1001",
                "Python 基础", 0.5, MemoryStatus.ACTIVE);
        when(vectorStore.search(eq(queryVec), eq("1001"), eq(5), anyDouble(),
                eq(MemoryStatus.ACTIVE), eq(MemoryStatus.DISTILLED)))
                .thenReturn(Arrays.asList(
                        new MemorySearchHit(r1, 0.9),
                        new MemorySearchHit(r2, 0.6)));

        // when
        CapturingObserver<RecallResponse> observer = new CapturingObserver<>();
        grpcService.recall(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        RecallResponse resp = observer.values.get(0);
        assertThat(resp.getMemoriesList()).hasSize(2);
        // 综合分排序：r1 score=0.9*(0.5+0.5*0.8)=0.81, r2 score=0.6*(0.5+0.5*0.5)=0.45 → r1 在前
        assertThat(resp.getMemories(0).getMemoryId()).isEqualTo("mem-1");
        assertThat(resp.getMemories(0).getRelevanceScore()).isEqualTo(0.9);
        assertThat(resp.getMeta().getTotalHits()).isEqualTo(2);
        assertThat(resp.getMeta().getReturned()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_RecallNoHits: 查无结果 → 返回空列表（非错误）")
    void should_ReturnEmpty_When_RecallNoHits() {
        // given
        RecallRequest req = RecallRequest.newBuilder()
                .setAgentId(1001L)
                .setQuery("不存在的查询")
                .setTopK(10)
                .build();
        when(embeddingClient.embed(anyString(), anyString())).thenReturn(new float[1024]);
        when(vectorStore.search(any(), anyString(), anyInt(), anyDouble(),
                eq(MemoryStatus.ACTIVE), eq(MemoryStatus.DISTILLED)))
                .thenReturn(Collections.emptyList());

        // when
        CapturingObserver<RecallResponse> observer = new CapturingObserver<>();
        grpcService.recall(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        RecallResponse resp = observer.values.get(0);
        assertThat(resp.getMemoriesList()).isEmpty();
        assertThat(resp.getMeta().getTotalHits()).isZero();
    }

    // ===== RPC 3: TriggerDistill =====

    @Test
    @DisplayName("Should_TriggerDistill_When_ActiveRecordsExist: 正常蒸馏 → 返回 DistillAck")
    void should_TriggerDistill_When_ActiveRecordsExist() {
        // given
        DistillRequest req = DistillRequest.newBuilder()
                .setAgentId(1001L)
                .setMaxMemories(50)
                .build();
        com.agent.memory.model.MemoryRecord r1 = buildRecord("mem-1", "1001",
                "Java 知识 1", 0.7, MemoryStatus.ACTIVE);
        r1.setTopic("coding");
        com.agent.memory.model.MemoryRecord r2 = buildRecord("mem-2", "1001",
                "Java 知识 2", 0.6, MemoryStatus.ACTIVE);
        r2.setTopic("coding");
        when(repository.findByTenantIdAndStatus("1001", MemoryStatus.ACTIVE))
                .thenReturn(Arrays.asList(r1, r2));
        com.agent.memory.model.MemoryRecord distilled = buildRecord("mem-distill", "1001",
                "Java 蒸馏摘要", 0.75, MemoryStatus.DISTILLED);
        when(distiller.distill(eq("1001"), eq("coding"), any())).thenReturn(distilled);

        // when
        CapturingObserver<DistillAck> observer = new CapturingObserver<>();
        grpcService.triggerDistill(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        DistillAck ack = observer.values.get(0);
        assertThat(ack.getDistilledCount()).isEqualTo(1);
        assertThat(ack.getMergedCount()).isEqualTo(2);
        verify(distiller).distill(eq("1001"), eq("coding"), any());
    }

    // ===== RPC 4: GetMemoryById =====

    @Test
    @DisplayName("Should_GetMemoryById_When_Exists: 按 ID 查询 → 返回 proto MemoryRecord")
    void should_GetMemoryById_When_Exists() {
        // given
        GetMemoryByIdRequest req = GetMemoryByIdRequest.newBuilder()
                .setMemoryId("mem-001")
                .build();
        com.agent.memory.model.MemoryRecord record = buildRecord("mem-001", "1001",
                "测试记忆内容", 0.85, MemoryStatus.ACTIVE);
        record.setUserId("user-001");
        record.setTopic("coding");
        record.setType(MemoryType.SEMANTIC);
        record.setSourceTaskId("task-001");
        record.setKeywords("[\"java\",\"programming\"]");
        record.setRecallCount(3);
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(Instant.now());
        when(repository.findByMemoryId("mem-001")).thenReturn(Optional.of(record));

        // when
        CapturingObserver<MemoryRecord> observer = new CapturingObserver<>();
        grpcService.getMemoryById(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        MemoryRecord proto = observer.values.get(0);
        assertThat(proto.getMemoryId()).isEqualTo("mem-001");
        assertThat(proto.getAgentId()).isEqualTo(1001L);
        assertThat(proto.getUserId()).isEqualTo("user-001");
        assertThat(proto.getDomain()).isEqualTo("coding");
        assertThat(proto.getMemoryType()).isEqualTo("semantic");
        assertThat(proto.getContent()).isEqualTo("测试记忆内容");
        assertThat(proto.getSourceTaskId()).isEqualTo("task-001");
        assertThat(proto.getImportanceScore()).isEqualTo(0.85);
        assertThat(proto.getAccessCount()).isEqualTo(3);
        assertThat(proto.getTagsList()).containsExactly("java", "programming");
    }

    @Test
    @DisplayName("Should_ThrowNotFound_When_MemoryNotExists: 不存在 → onError NOT_FOUND")
    void should_ThrowNotFound_When_MemoryNotExists() {
        // given
        GetMemoryByIdRequest req = GetMemoryByIdRequest.newBuilder()
                .setMemoryId("not-exist")
                .build();
        when(repository.findByMemoryId("not-exist")).thenReturn(Optional.empty());

        // when
        CapturingObserver<MemoryRecord> observer = new CapturingObserver<>();
        grpcService.getMemoryById(req, observer);

        // then
        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        verify(repository).findByMemoryId("not-exist");
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_MemoryIdEmpty: 空 memory_id → onError INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_MemoryIdEmpty() {
        // given
        GetMemoryByIdRequest req = GetMemoryByIdRequest.newBuilder()
                .setMemoryId("")
                .build();

        // when
        CapturingObserver<MemoryRecord> observer = new CapturingObserver<>();
        grpcService.getMemoryById(req, observer);

        // then
        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(repository, never()).findByMemoryId(any());
    }

    // ===== 辅助方法 =====

    private com.agent.memory.model.MemoryRecord buildRecord(String memoryId, String tenantId,
                                                             String content, double importance,
                                                             MemoryStatus status) {
        com.agent.memory.model.MemoryRecord r = new com.agent.memory.model.MemoryRecord();
        r.setMemoryId(memoryId);
        r.setTenantId(tenantId);
        r.setContent(content);
        r.setImportanceScore(importance);
        r.setImportanceLevel(importance >= 0.7 ? "HIGH" : (importance >= 0.4 ? "MEDIUM" : "LOW"));
        r.setStatus(status);
        r.setType(MemoryType.SEMANTIC);
        return r;
    }

    /** 捕获 onNext/onError/onCompleted 的 StreamObserver。 */
    private static class CapturingObserver<T> implements StreamObserver<T> {
        final List<T> values = new ArrayList<>();
        Throwable error;
        boolean completed;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
