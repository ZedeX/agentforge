package com.agent.knowledge.grpc;

import agentplatform.knowledge.v1.DeleteBaseRequest;
import agentplatform.knowledge.v1.DeleteBaseResponse;
import agentplatform.knowledge.v1.IngestDocumentRequest;
import agentplatform.knowledge.v1.IngestDocumentResponse;
import agentplatform.knowledge.v1.KnowledgeBaseInfo;
import agentplatform.knowledge.v1.ListBasesRequest;
import agentplatform.knowledge.v1.ListBasesResponse;
import agentplatform.knowledge.v1.SearchChunksRequest;
import agentplatform.knowledge.v1.SearchChunksResponse;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.knowledge.api.KnowledgeBaseService;
import com.agent.knowledge.model.IngestResult;
import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.model.SearchResult;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeBaseGrpcService} 单测（Plan 08 T11）。
 *
 * <p>纯单测：mock {@link KnowledgeBaseService} + 真实 {@link KnowledgeMapper} +
 * 真实 {@link GrpcExceptionAdvice}，用 capturing StreamObserver 捕获 onNext/onError。
 * 验证 4 RPC 正常流 + 异常翻译（KB_NOT_FOUND → NOT_FOUND，KB_IN_USE → FAILED_PRECONDITION）。</p>
 */
@DisplayName("KnowledgeBaseGrpcService gRPC 4 RPC")
class KnowledgeBaseGrpcServiceTest {

    private KnowledgeBaseService kbService;
    private KnowledgeBaseGrpcService grpcService;

    @BeforeEach
    void setUp() {
        kbService = mock(KnowledgeBaseService.class);
        KnowledgeMapper mapper = new KnowledgeMapper();
        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();
        grpcService = new KnowledgeBaseGrpcService(kbService, mapper, advice);
    }

    // ===== IngestDocument =====

    @Test
    @DisplayName("IngestDocument: 正常 → IngestDocumentResponse success=true")
    void should_ReturnIngestResponse_When_IngestDocumentSuccess() {
        IngestResult result = new IngestResult("doc-1", "kb-1",
                Arrays.asList("c1", "c2"), true, "ok", 2, 100);
        when(kbService.ingest(anyString(), anyString(), anyString(), anyString(),
                Mockito.any(), Mockito.any(), anyInt(), anyInt()))
                .thenReturn(result);

        IngestDocumentRequest req = IngestDocumentRequest.newBuilder()
                .setKbId("kb-1").setDocId("doc-1").setName("n").setContent("c")
                .setType("TEXT").setChunkStrategy("TOKEN")
                .setMaxTokens(512).setOverlap(64).build();
        CapturingObserver<IngestDocumentResponse> obs = new CapturingObserver<>();
        grpcService.ingestDocument(req, obs);

        assertThat(obs.completed).isTrue();
        assertThat(obs.value).isNotNull();
        assertThat(obs.value.getSuccess()).isTrue();
        assertThat(obs.value.getDocId()).isEqualTo("doc-1");
        assertThat(obs.value.getChunkCount()).isEqualTo(2);
        assertThat(obs.value.getChunkIdsList()).containsExactly("c1", "c2");
    }

    @Test
    @DisplayName("IngestDocument: KB 不存在 → onError NOT_FOUND")
    void should_ReturnError_When_IngestDocumentKbNotFound() {
        when(kbService.ingest(anyString(), anyString(), anyString(), anyString(),
                Mockito.any(), Mockito.any(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.KB_NOT_FOUND, "missing"));

        IngestDocumentRequest req = IngestDocumentRequest.newBuilder()
                .setKbId("kb-x").setContent("c").build();
        CapturingObserver<IngestDocumentResponse> obs = new CapturingObserver<>();
        grpcService.ingestDocument(req, obs);

        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) obs.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(sre.getStatus().getDescription()).contains("KB_NOT_FOUND");
    }

    // ===== SearchChunks =====

    @Test
    @DisplayName("SearchChunks: 正常 → SearchChunksResponse 含 chunks")
    void should_ReturnSearchResponse_When_SearchChunksSuccess() {
        List<SearchResult> results = Arrays.asList(
                new SearchResult("c1", 0.9, "content1", "d1", "kb-1"),
                new SearchResult("c2", 0.7, "content2", "d2", "kb-1"));
        when(kbService.search(anyString(), anyString(), anyInt(), anyBoolean()))
                .thenReturn(results);

        SearchChunksRequest req = SearchChunksRequest.newBuilder()
                .setKbId("kb-1").setQuery("q").setTopK(5).setEnableMmr(true).build();
        CapturingObserver<SearchChunksResponse> obs = new CapturingObserver<>();
        grpcService.searchChunks(req, obs);

        assertThat(obs.completed).isTrue();
        assertThat(obs.value.getChunksList()).hasSize(2);
        assertThat(obs.value.getChunks(0).getChunkId()).isEqualTo("c1");
        assertThat(obs.value.getChunks(0).getScore()).isEqualTo(0.9);
        assertThat(obs.value.getTotalHits()).isEqualTo(2);
    }

    // ===== ListBases =====

    @Test
    @DisplayName("ListBases: 正常 → ListBasesResponse 含 bases")
    void should_ReturnListResponse_When_ListBases() {
        KnowledgeBase kb1 = new KnowledgeBase("kb-1", "KB1");
        kb1.setStatus(com.agent.knowledge.enums.KnowledgeStatus.READY);
        kb1.setDocCount(2);
        when(kbService.listBases(Mockito.nullable(String.class)))
                .thenReturn(Collections.singletonList(kb1));

        ListBasesRequest req = ListBasesRequest.newBuilder().setStatus("READY").build();
        CapturingObserver<ListBasesResponse> obs = new CapturingObserver<>();
        grpcService.listBases(req, obs);

        assertThat(obs.completed).isTrue();
        assertThat(obs.value.getBasesList()).hasSize(1);
        KnowledgeBaseInfo info = obs.value.getBases(0);
        assertThat(info.getKbId()).isEqualTo("kb-1");
        assertThat(info.getStatus()).isEqualTo("READY");
        assertThat(info.getDocCount()).isEqualTo(2);
        assertThat(obs.value.getTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("ListBases: 空过滤 → 调用 listBases(null)")
    void should_CallListBasesNull_When_StatusEmpty() {
        when(kbService.listBases(Mockito.nullable(String.class)))
                .thenReturn(Collections.emptyList());

        ListBasesRequest req = ListBasesRequest.newBuilder().setStatus("").build();
        CapturingObserver<ListBasesResponse> obs = new CapturingObserver<>();
        grpcService.listBases(req, obs);

        assertThat(obs.completed).isTrue();
        assertThat(obs.value.getTotal()).isEqualTo(0);
    }

    // ===== DeleteBase =====

    @Test
    @DisplayName("DeleteBase: 正常 → DeleteBaseResponse success=true")
    void should_ReturnDeleteResponse_When_DeleteBaseSuccess() {
        when(kbService.deleteBase(anyString(), anyBoolean())).thenReturn(true);

        DeleteBaseRequest req = DeleteBaseRequest.newBuilder()
                .setKbId("kb-1").setForce(false).build();
        CapturingObserver<DeleteBaseResponse> obs = new CapturingObserver<>();
        grpcService.deleteBase(req, obs);

        assertThat(obs.completed).isTrue();
        assertThat(obs.value.getSuccess()).isTrue();
        assertThat(obs.value.getKbId()).isEqualTo("kb-1");
    }

    @Test
    @DisplayName("DeleteBase: KB_IN_USE → onError FAILED_PRECONDITION")
    void should_ReturnError_When_DeleteBaseKbInUse() {
        when(kbService.deleteBase(anyString(), anyBoolean()))
                .thenThrow(new BusinessException(ErrorCode.KB_IN_USE, "in use"));

        DeleteBaseRequest req = DeleteBaseRequest.newBuilder()
                .setKbId("kb-1").setForce(false).build();
        CapturingObserver<DeleteBaseResponse> obs = new CapturingObserver<>();
        grpcService.deleteBase(req, obs);

        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) obs.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(sre.getStatus().getDescription()).contains("KB_IN_USE");
    }

    // ===== capturing observer =====

    private static class CapturingObserver<T> implements StreamObserver<T> {
        T value;
        Throwable error;
        boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
