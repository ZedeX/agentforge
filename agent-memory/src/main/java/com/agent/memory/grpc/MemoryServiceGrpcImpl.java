package com.agent.memory.grpc;

import agentplatform.memory.v1.DistillAck;
import agentplatform.memory.v1.DistillRequest;
import agentplatform.memory.v1.GetMemoryByIdRequest;
import agentplatform.memory.v1.MemoryServiceGrpc;
import agentplatform.memory.v1.RecallMeta;
import agentplatform.memory.v1.RecallRequest;
import agentplatform.memory.v1.RecallResponse;
import agentplatform.memory.v1.RecalledMemory;
import agentplatform.memory.v1.WriteAck;
import agentplatform.memory.v1.WriteLongTermRequest;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.memory.api.EmbeddingClient;
import com.agent.memory.api.LongTermMemoryWriter;
import com.agent.memory.api.MemoryDistiller;
import com.agent.memory.api.MemoryVectorStore;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.MemorySearchHit;
import com.agent.memory.repository.MemoryRecordRepository;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MemoryService gRPC 服务端实现（Plan 03 T10，4 RPC）。
 *
 * <p>覆盖 {@link MemoryServiceGrpc.MemoryServiceImplBase} 的 4 个 RPC：
 * {@code writeLongTerm} / {@code recall} / {@code triggerDistill} / {@code getMemoryById}。</p>
 *
 * <p>职责：proto request → 调用业务服务 → {@link MemoryRecordMapper} 转 proto response → 下发 observer。
 * 异常通过 {@link GrpcExceptionAdvice} 统一翻译为 gRPC Status。</p>
 *
 * <p>关键业务规则：</p>
 * <ul>
 *   <li>WriteLongTerm：校验 content 非空 → mapper 转 entity → LongTermMemoryWriter.write → 返回 WriteAck</li>
 *   <li>Recall：校验 query 非空 → EmbeddingClient.embed → MemoryVectorStore.search →
 *       综合分排序（score × (0.5 + 0.5 × importance)）→ 映射 RecalledMemory</li>
 *   <li>TriggerDistill：查 ACTIVE 记忆按 topic 分组 → MemoryDistiller.distill → 统计返回 DistillAck</li>
 *   <li>GetMemoryById：findByMemoryId → 不存在抛 MEMORY_NOT_FOUND → 返回 proto MemoryRecord</li>
 * </ul>
 */
@Slf4j
@GrpcService
public class MemoryServiceGrpcImpl extends MemoryServiceGrpc.MemoryServiceImplBase {

    private final LongTermMemoryWriter writer;
    private final EmbeddingClient embeddingClient;
    private final MemoryVectorStore vectorStore;
    private final MemoryDistiller distiller;
    private final MemoryRecordRepository repository;
    private final MemoryRecordMapper mapper;
    private final GrpcExceptionAdvice exceptionAdvice;

    public MemoryServiceGrpcImpl(LongTermMemoryWriter writer,
                                  EmbeddingClient embeddingClient,
                                  MemoryVectorStore vectorStore,
                                  MemoryDistiller distiller,
                                  MemoryRecordRepository repository,
                                  MemoryRecordMapper mapper,
                                  GrpcExceptionAdvice exceptionAdvice) {
        this.writer = writer;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.distiller = distiller;
        this.repository = repository;
        this.mapper = mapper;
        this.exceptionAdvice = exceptionAdvice;
    }

    // ===== RPC 1: WriteLongTerm =====

    @Override
    public void writeLongTerm(WriteLongTermRequest request,
                              StreamObserver<WriteAck> responseObserver) {
        try {
            if (request.getContent() == null || request.getContent().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "content must not be empty");
            }
            MemoryRecord entity = mapper.toEntity(request);
            String memoryId = writer.write(entity);
            WriteAck ack = WriteAck.newBuilder()
                    .setMemoryId(memoryId == null ? "" : memoryId)
                    .setDeduplicated(memoryId == null)
                    .setMergedMemoryId("")
                    .build();
            log.info("writeLongTerm success agent_id={} memoryId={}",
                    request.getAgentId(), memoryId);
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 2: Recall =====

    @Override
    public void recall(RecallRequest request,
                       StreamObserver<RecallResponse> responseObserver) {
        try {
            if (request.getQuery() == null || request.getQuery().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "query must not be empty");
            }
            String tenantId = String.valueOf(request.getAgentId());
            int topK = request.getTopK() > 0 ? request.getTopK() : 10;
            double scoreThreshold = 0.75; // 默认阈值，对齐 doc 04 §8.2

            // 1. 查询向量化
            float[] queryVector = embeddingClient.embed(request.getQuery(), tenantId);

            // 2. 向量检索
            List<MemorySearchHit> hits = vectorStore.search(
                    queryVector, tenantId, topK, scoreThreshold,
                    MemoryStatus.ACTIVE, MemoryStatus.DISTILLED);

            if (hits.isEmpty()) {
                responseObserver.onNext(mapper.emptyRecallResponse());
                responseObserver.onCompleted();
                return;
            }

            // 3. 综合排序：score × (0.5 + 0.5 × importance)
            List<MemorySearchHit> ranked = hits.stream()
                    .sorted(Comparator.comparingDouble(
                            (MemorySearchHit h) -> h.getScore() * (0.5 + 0.5 * h.getRecord().getImportanceScore())
                    ).reversed())
                    .limit(topK)
                    .collect(Collectors.toList());

            // 4. 映射 RecalledMemory
            List<RecalledMemory> memories = ranked.stream()
                    .map(h -> mapper.toRecalledMemory(h.getRecord(), h.getScore()))
                    .collect(Collectors.toList());

            RecallResponse response = RecallResponse.newBuilder()
                    .addAllMemories(memories)
                    .setMeta(RecallMeta.newBuilder()
                            .setTotalHits(hits.size())
                            .setReturned(memories.size())
                            .setTokenUsed(0)
                            .setStrategiesUsed("vector")
                            .build())
                    .build();
            log.info("recall success agent_id={} query_len={} hits={}",
                    request.getAgentId(), request.getQuery().length(), memories.size());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 3: TriggerDistill =====

    @Override
    public void triggerDistill(DistillRequest request,
                               StreamObserver<DistillAck> responseObserver) {
        try {
            if (request.getAgentId() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "agent_id must be positive");
            }
            String tenantId = String.valueOf(request.getAgentId());
            // 查询所有 ACTIVE 记忆，按 topic 分组蒸馏
            List<MemoryRecord> activeRecords = repository.findByTenantIdAndStatus(tenantId, MemoryStatus.ACTIVE);
            Map<String, List<MemoryRecord>> byTopic = activeRecords.stream()
                    .filter(r -> r.getTopic() != null)
                    .collect(Collectors.groupingBy(MemoryRecord::getTopic));

            int distilledCount = 0;
            int mergedCount = 0;
            for (Map.Entry<String, List<MemoryRecord>> entry : byTopic.entrySet()) {
                MemoryRecord distilled = distiller.distill(tenantId, entry.getKey(), entry.getValue());
                if (distilled != null) {
                    distilledCount++;
                    mergedCount += entry.getValue().size();
                }
            }
            DistillAck ack = DistillAck.newBuilder()
                    .setDistilledCount(distilledCount)
                    .setMergedCount(mergedCount)
                    .setPrunedCount(0)
                    .build();
            log.info("triggerDistill success agent_id={} topics={} distilled={} merged={}",
                    request.getAgentId(), byTopic.size(), distilledCount, mergedCount);
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 4: GetMemoryById =====

    @Override
    public void getMemoryById(GetMemoryByIdRequest request,
                              StreamObserver<agentplatform.memory.v1.MemoryRecord> responseObserver) {
        try {
            if (request.getMemoryId() == null || request.getMemoryId().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "memory_id must not be empty");
            }
            Optional<MemoryRecord> record = repository.findByMemoryId(request.getMemoryId());
            if (record.isEmpty()) {
                throw new BusinessException(ErrorCode.MEMORY_NOT_FOUND,
                        "memory not found: " + request.getMemoryId());
            }
            agentplatform.memory.v1.MemoryRecord proto = mapper.toProto(record.get());
            log.info("getMemoryById success memoryId={}", request.getMemoryId());
            responseObserver.onNext(proto);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }
}
