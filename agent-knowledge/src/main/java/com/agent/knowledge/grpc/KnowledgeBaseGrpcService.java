package com.agent.knowledge.grpc;

import agentplatform.knowledge.v1.DeleteBaseRequest;
import agentplatform.knowledge.v1.DeleteBaseResponse;
import agentplatform.knowledge.v1.IngestDocumentRequest;
import agentplatform.knowledge.v1.IngestDocumentResponse;
import agentplatform.knowledge.v1.KnowledgeServiceGrpc;
import agentplatform.knowledge.v1.ListBasesRequest;
import agentplatform.knowledge.v1.ListBasesResponse;
import agentplatform.knowledge.v1.SearchChunksRequest;
import agentplatform.knowledge.v1.SearchChunksResponse;
import com.agent.knowledge.api.KnowledgeBaseService;
import com.agent.knowledge.enums.ChunkStrategyType;
import com.agent.knowledge.enums.DocumentType;
import com.agent.knowledge.model.IngestResult;
import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.model.SearchResult;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

/**
 * KnowledgeService gRPC 服务端实现（Plan 08 T11，4 新 RPC）。
 *
 * <p>覆盖 {@link KnowledgeServiceGrpc.KnowledgeServiceImplBase} 的 4 个新 RPC：
 * {@code ingestDocument} / {@code searchChunks} / {@code listBases} / {@code deleteBase}。
 * 现有 3 个旧 RPC（Retrieve / Ingest / VersionManage）保留 default UNIMPLEMENTED。</p>
 *
 * <p>职责：proto request → 调用 {@link KnowledgeBaseService} → {@link KnowledgeMapper} 转 proto
 * response → 下发 observer。异常通过 {@link GrpcExceptionAdvice} 统一翻译为 gRPC Status。</p>
 */
@Slf4j
@GrpcService
public class KnowledgeBaseGrpcService extends KnowledgeServiceGrpc.KnowledgeServiceImplBase {

    private final KnowledgeBaseService kbService;
    private final KnowledgeMapper mapper;
    private final GrpcExceptionAdvice exceptionAdvice;

    public KnowledgeBaseGrpcService(KnowledgeBaseService kbService,
                                    KnowledgeMapper mapper,
                                    GrpcExceptionAdvice exceptionAdvice) {
        this.kbService = kbService;
        this.mapper = mapper;
        this.exceptionAdvice = exceptionAdvice;
    }

    // ===== RPC 1: IngestDocument =====

    @Override
    public void ingestDocument(IngestDocumentRequest request,
                               StreamObserver<IngestDocumentResponse> responseObserver) {
        try {
            DocumentType type = DocumentType.fromCode(request.getType());
            ChunkStrategyType strategy = ChunkStrategyType.fromCode(request.getChunkStrategy());
            IngestResult result = kbService.ingest(
                    request.getKbId(),
                    request.getDocId(),
                    request.getName(),
                    request.getContent(),
                    type,
                    strategy,
                    request.getMaxTokens(),
                    request.getOverlap());
            responseObserver.onNext(mapper.toIngestResponse(result));
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 2: SearchChunks =====

    @Override
    public void searchChunks(SearchChunksRequest request,
                             StreamObserver<SearchChunksResponse> responseObserver) {
        try {
            List<SearchResult> results = kbService.search(
                    request.getKbId(),
                    request.getQuery(),
                    request.getTopK(),
                    request.getEnableMmr());
            responseObserver.onNext(mapper.toSearchResponse(results));
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 3: ListBases =====

    @Override
    public void listBases(ListBasesRequest request,
                          StreamObserver<ListBasesResponse> responseObserver) {
        try {
            List<KnowledgeBase> bases = kbService.listBases(request.getStatus().isEmpty() ? null : request.getStatus());
            responseObserver.onNext(mapper.toListResponse(bases));
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 4: DeleteBase =====

    @Override
    public void deleteBase(DeleteBaseRequest request,
                           StreamObserver<DeleteBaseResponse> responseObserver) {
        try {
            String kbId = request.getKbId();
            kbService.deleteBase(kbId, request.getForce());
            responseObserver.onNext(mapper.toDeleteResponse(kbId, true, "deleted"));
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }
}
