package com.agent.knowledge.api.impl;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.knowledge.api.DocumentIngestor;
import com.agent.knowledge.api.KnowledgeBaseService;
import com.agent.knowledge.api.KnowledgeRetriever;
import com.agent.knowledge.enums.ChunkStrategyType;
import com.agent.knowledge.enums.DocumentType;
import com.agent.knowledge.enums.KnowledgeStatus;
import com.agent.knowledge.model.IngestResult;
import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.model.SearchResult;
import com.agent.knowledge.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeBaseServiceImpl} 单测（Plan 08 T11）。
 *
 * <p>@DataJpaTest 加载真实 H2 + KnowledgeBaseRepository；@Import 加载被测 @Service；
 * @MockBean 替换 DocumentIngestor / KnowledgeRetriever 隔离业务逻辑。</p>
 */
@DataJpaTest
@Import(KnowledgeBaseServiceImpl.class)
@ActiveProfiles("test")
@DisplayName("KnowledgeBaseServiceImpl JPA 知识库管理")
class KnowledgeBaseServiceImplTest {

    @Autowired
    private KnowledgeBaseService kbService;

    @Autowired
    private KnowledgeBaseRepository kbRepository;

    @MockBean
    private DocumentIngestor documentIngestor;

    @MockBean
    private KnowledgeRetriever knowledgeRetriever;

    // ===== createBase =====

    @Test
    @DisplayName("createBase: 正常创建 → status=READY 持久化")
    void should_CreateBase_When_NormalFlow() {
        KnowledgeBase kb = kbService.createBase("kb-test-1", "测试KB", "描述");

        assertThat(kb.getKbId()).isEqualTo("kb-test-1");
        assertThat(kb.getName()).isEqualTo("测试KB");
        assertThat(kb.getStatus()).isEqualTo(KnowledgeStatus.READY);
        assertThat(kb.getCreatedAt()).isGreaterThan(0);
        assertThat(kbRepository.existsByKbId("kb-test-1")).isTrue();
    }

    @Test
    @DisplayName("createBase: name 空 → PARAM_INVALID")
    void should_ThrowParamInvalid_When_NameEmpty() {
        assertThatThrownBy(() -> kbService.createBase("kb-x", "", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    @DisplayName("createBase: kbId 重复 → PARAM_INVALID")
    void should_ThrowParamInvalid_When_KbIdDuplicate() {
        kbService.createBase("kb-dup", "KB1", null);
        assertThatThrownBy(() -> kbService.createBase("kb-dup", "KB2", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    @DisplayName("createBase: kbId null → 自动生成 kb- 前缀")
    void should_AutoGenerateKbId_When_KbIdNull() {
        KnowledgeBase kb = kbService.createBase(null, "自动ID", null);
        assertThat(kb.getKbId()).startsWith("kb-");
        assertThat(kb.getKbId().length()).isGreaterThan(4);
    }

    // ===== ingest =====

    @Test
    @DisplayName("ingest: KB 不存在 → KB_NOT_FOUND")
    void should_ThrowKbNotFound_When_IngestKbMissing() {
        when(documentIngestor.ingestDocument(anyString(), any(), any(), any(),
                any(), any(), anyInt(), anyInt()))
                .thenReturn(successResult("doc-1", "kb-missing"));

        assertThatThrownBy(() -> kbService.ingest("kb-missing", "doc-1", "n", "c",
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 512, 64))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.KB_NOT_FOUND));
    }

    @Test
    @DisplayName("ingest: KB 已删除 → KB_NOT_FOUND")
    void should_ThrowKbNotFound_When_IngestKbDeleted() {
        KnowledgeBase kb = kbService.createBase("kb-del", "已删KB", null);
        kb.setStatus(KnowledgeStatus.DELETED);
        kbRepository.save(kb);

        assertThatThrownBy(() -> kbService.ingest("kb-del", "doc-1", "n", "c",
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 512, 64))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.KB_NOT_FOUND));
    }

    @Test
    @DisplayName("ingest: DocumentIngestor 返回 failure → DOC_INGEST_FAILED")
    void should_ThrowDocIngestFailed_When_IngestorReturnsFailure() {
        kbService.createBase("kb-ing", "KB", null);
        when(documentIngestor.ingestDocument(anyString(), any(), any(), any(),
                any(), any(), anyInt(), anyInt()))
                .thenReturn(new IngestResult("doc-1", "kb-ing", Collections.emptyList(),
                        false, "content empty", 0, 0));

        assertThatThrownBy(() -> kbService.ingest("kb-ing", "doc-1", "n", "c",
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 512, 64))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DOC_INGEST_FAILED));
    }

    @Test
    @DisplayName("ingest: 正常入库 → 返回 IngestResult")
    void should_ReturnResult_When_IngestSuccess() {
        kbService.createBase("kb-ok", "KB", null);
        IngestResult mockResult = successResult("doc-1", "kb-ok");
        when(documentIngestor.ingestDocument(anyString(), any(), any(), any(),
                any(), any(), anyInt(), anyInt()))
                .thenReturn(mockResult);

        IngestResult result = kbService.ingest("kb-ok", "doc-1", "n", "content",
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 512, 64);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDocId()).isEqualTo("doc-1");
    }

    // ===== search =====

    @Test
    @DisplayName("search: KB 不存在 → KB_NOT_FOUND")
    void should_ThrowKbNotFound_When_SearchKbMissing() {
        assertThatThrownBy(() -> kbService.search("kb-none", "q", 5, false))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.KB_NOT_FOUND));
    }

    @Test
    @DisplayName("search: 正常检索 → 返回 SearchResult 列表")
    void should_ReturnResults_When_SearchSuccess() {
        kbService.createBase("kb-srch", "KB", null);
        List<SearchResult> mockResults = Arrays.asList(
                new SearchResult("c1", 0.9, "content1", "d1", "kb-srch"));
        when(knowledgeRetriever.search(any())).thenReturn(mockResults);

        List<SearchResult> results = kbService.search("kb-srch", "query", 5, true);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getChunkId()).isEqualTo("c1");
    }

    // ===== listBases =====

    @Test
    @DisplayName("listBases: 空过滤 → 返回全部非 DELETED")
    void should_ReturnAllNonDeleted_When_ListBasesNoFilter() {
        kbService.createBase("kb-a", "A", null);
        kbService.createBase("kb-b", "B", null);
        KnowledgeBase deleted = kbService.createBase("kb-c", "C", null);
        deleted.setStatus(KnowledgeStatus.DELETED);
        kbRepository.save(deleted);

        List<KnowledgeBase> bases = kbService.listBases(null);

        assertThat(bases).extracting(KnowledgeBase::getKbId)
                .containsExactlyInAnyOrder("kb-a", "kb-b")
                .doesNotContain("kb-c");
    }

    @Test
    @DisplayName("listBases: status=READY → 按状态过滤")
    void should_ReturnByStatus_When_ListBasesWithFilter() {
        kbService.createBase("kb-r1", "R1", null);
        kbService.createBase("kb-r2", "R2", null);

        List<KnowledgeBase> bases = kbService.listBases("ready");

        assertThat(bases).isNotEmpty();
        assertThat(bases).allSatisfy(kb -> assertThat(kb.getStatus()).isEqualTo(KnowledgeStatus.READY));
    }

    // ===== deleteBase =====

    @Test
    @DisplayName("deleteBase: KB 不存在 → KB_NOT_FOUND")
    void should_ThrowKbNotFound_When_DeleteKbMissing() {
        assertThatThrownBy(() -> kbService.deleteBase("kb-none", false))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.KB_NOT_FOUND));
    }

    @Test
    @DisplayName("deleteBase: 有文档且非 force → KB_IN_USE")
    void should_ThrowKbInUse_When_DeleteWithDocsAndNoForce() {
        KnowledgeBase kb = kbService.createBase("kb-use", "KB", null);
        kb.setDocCount(3);
        kbRepository.save(kb);

        assertThatThrownBy(() -> kbService.deleteBase("kb-use", false))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.KB_IN_USE));
    }

    @Test
    @DisplayName("deleteBase: 有文档且 force=true → 软删成功")
    void should_Delete_When_DeleteWithForce() {
        KnowledgeBase kb = kbService.createBase("kb-force", "KB", null);
        kb.setDocCount(2);
        kbRepository.save(kb);

        boolean result = kbService.deleteBase("kb-force", true);

        assertThat(result).isTrue();
        KnowledgeBase updated = kbRepository.findByKbId("kb-force").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(KnowledgeStatus.DELETED);
    }

    @Test
    @DisplayName("deleteBase: 无文档 → 软删成功")
    void should_Delete_When_DeleteNoDocs() {
        kbService.createBase("kb-empty", "KB", null);

        boolean result = kbService.deleteBase("kb-empty", false);

        assertThat(result).isTrue();
        KnowledgeBase updated = kbRepository.findByKbId("kb-empty").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(KnowledgeStatus.DELETED);
    }

    // ===== helper =====

    private IngestResult successResult(String docId, String kbId) {
        return new IngestResult(docId, kbId, Arrays.asList("chunk-1", "chunk-2"),
                true, "ok", 2, 100);
    }
}
