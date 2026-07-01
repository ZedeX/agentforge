package com.agent.knowledge.grpc;

import agentplatform.knowledge.v1.DeleteBaseResponse;
import agentplatform.knowledge.v1.IngestDocumentResponse;
import agentplatform.knowledge.v1.KnowledgeBaseInfo;
import agentplatform.knowledge.v1.KnowledgeChunk;
import agentplatform.knowledge.v1.ListBasesResponse;
import agentplatform.knowledge.v1.SearchChunksResponse;
import com.agent.knowledge.model.IngestResult;
import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.model.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Proto ↔ Entity / POJO 映射器（Plan 08 T11）。
 *
 * <p>将 JPA Entity {@link KnowledgeBase}、POJO {@link SearchResult} / {@link IngestResult}
 * 转换为 gRPC proto response 消息。单向（业务对象 → proto），proto request 由 GrpcService 直接取字段。</p>
 */
@Component
public class KnowledgeMapper {

    /**
     * KnowledgeBase Entity → KnowledgeBaseInfo proto.
     */
    public KnowledgeBaseInfo toInfo(KnowledgeBase kb) {
        return KnowledgeBaseInfo.newBuilder()
                .setKbId(kb.getKbId())
                .setName(kb.getName())
                .setDescription(kb.getDescription() == null ? "" : kb.getDescription())
                .setStatus(kb.getStatus().name())
                .setDocCount(kb.getDocCount())
                .setChunkCount(kb.getChunkCount())
                .setCreatedAt(kb.getCreatedAt())
                .setUpdatedAt(kb.getUpdatedAt())
                .build();
    }

    /**
     * SearchResult POJO → KnowledgeChunk proto.
     */
    public KnowledgeChunk toChunk(SearchResult sr) {
        return KnowledgeChunk.newBuilder()
                .setChunkId(sr.getChunkId())
                .setDocId(sr.getDocId())
                .setContent(sr.getContent())
                .setScore(sr.getScore())
                .build();
    }

    /**
     * IngestResult → IngestDocumentResponse proto.
     */
    public IngestDocumentResponse toIngestResponse(IngestResult result) {
        IngestDocumentResponse.Builder b = IngestDocumentResponse.newBuilder()
                .setDocId(result.getDocId())
                .setKbId(result.getKbId())
                .setSuccess(result.isSuccess())
                .setMessage(result.getMessage() == null ? "" : result.getMessage())
                .setChunkCount(result.getChunkCount())
                .setTokenCount(result.getTokenCount());
        b.addAllChunkIds(result.getChunkIds());
        return b.build();
    }

    /**
     * List&lt;SearchResult&gt; → SearchChunksResponse proto.
     */
    public SearchChunksResponse toSearchResponse(List<SearchResult> results) {
        SearchChunksResponse.Builder b = SearchChunksResponse.newBuilder()
                .setTotalHits(results.size());
        for (SearchResult sr : results) {
            b.addChunks(toChunk(sr));
        }
        return b.build();
    }

    /**
     * List&lt;KnowledgeBase&gt; → ListBasesResponse proto.
     */
    public ListBasesResponse toListResponse(List<KnowledgeBase> bases) {
        ListBasesResponse.Builder b = ListBasesResponse.newBuilder()
                .setTotal(bases.size());
        for (KnowledgeBase kb : bases) {
            b.addBases(toInfo(kb));
        }
        return b.build();
    }

    /**
     * 删除结果 → DeleteBaseResponse proto.
     */
    public DeleteBaseResponse toDeleteResponse(String kbId, boolean success, String message) {
        return DeleteBaseResponse.newBuilder()
                .setKbId(kbId)
                .setSuccess(success)
                .setMessage(message == null ? "" : message)
                .build();
    }
}
