package com.agent.knowledge.api.impl;

import com.agent.knowledge.api.DocumentIngestor;
import com.agent.knowledge.enums.ChunkStrategyType;
import com.agent.knowledge.enums.DocumentType;
import com.agent.knowledge.enums.IngestStatus;
import com.agent.knowledge.enums.KnowledgeStatus;
import com.agent.knowledge.model.DocumentChunk;
import com.agent.knowledge.model.IngestResult;
import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.model.KnowledgeDocument;
import com.agent.knowledge.repository.DocumentChunkRepository;
import com.agent.knowledge.repository.KnowledgeBaseRepository;
import com.agent.knowledge.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentIngestorImpl JPA integration tests (Plan 08 T9).
 *
 * <p>Uses @DataJpaTest + @Import to load DocumentIngestorImpl along with its
 * skeleton-stage dependencies (DocumentParserImpl / ChunkSplitterImpl).
 * Verifies full ingest flow: parse → split → persist chunks → update KB stats.</p>
 */
@DisplayName("DocumentIngestorImpl 文档导入器测试")
@DataJpaTest
@Import({DocumentIngestorImpl.class, DocumentParserImpl.class, ChunkSplitterImpl.class})
@ActiveProfiles("test")
class DocumentIngestorImplTest {

    @Autowired
    private DocumentIngestor ingestor;

    @Autowired
    private KnowledgeBaseRepository kbRepository;

    @Autowired
    private KnowledgeDocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    private KnowledgeBase createKb(String kbId, String name, KnowledgeStatus status) {
        KnowledgeBase kb = new KnowledgeBase(kbId, name);
        kb.setStatus(status);
        return kbRepository.save(kb);
    }

    @Test
    @DisplayName("正常导入: 创建 KnowledgeDocument + DocumentChunk[] + KB 统计更新")
    void should_IngestDocument_When_NormalFlow() {
        KnowledgeBase kb = createKb("kb-normal", "TestKB", KnowledgeStatus.READY);
        // 100 chars → TOKEN strategy with maxTokens=10 (40 chars/chunk) → ~3 chunks
        String content = "a".repeat(100);

        IngestResult result = ingestor.ingestDocument(
                "kb-normal", "doc-001", "TestDoc", content,
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 10, 2);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDocId()).isEqualTo("doc-001");
        assertThat(result.getKbId()).isEqualTo("kb-normal");
        assertThat(result.getChunkCount()).isGreaterThan(1);
        assertThat(result.getChunkIds()).hasSize(result.getChunkCount());

        // Verify KnowledgeDocument persisted
        KnowledgeDocument doc = documentRepository.findByDocId("doc-001").orElseThrow();
        assertThat(doc.getName()).isEqualTo("TestDoc");
        assertThat(doc.getKbId()).isEqualTo("kb-normal");
        assertThat(doc.getChunkCount()).isEqualTo(result.getChunkCount());

        // Verify DocumentChunk[] persisted
        List<DocumentChunk> chunks = chunkRepository.findByKbIdAndDocId("kb-normal", "doc-001");
        assertThat(chunks).hasSize(result.getChunkCount());
        assertThat(chunks).allMatch(c -> c.getStatus() == IngestStatus.PENDING);

        // Verify KB stats updated
        KnowledgeBase updatedKb = kbRepository.findByKbId("kb-normal").orElseThrow();
        assertThat(updatedKb.getDocCount()).isEqualTo(1);
        assertThat(updatedKb.getChunkCount()).isEqualTo(result.getChunkCount());
    }

    @Test
    @DisplayName("KB 不存在: 返回 failure, 不创建任何记录")
    void should_ReturnFailure_When_KbNotFound() {
        IngestResult result = ingestor.ingestDocument(
                "kb-missing", "doc-x", "Name", "content",
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 512, 64);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("KB not found");
        assertThat(documentRepository.findByDocId("doc-x")).isEmpty();
    }

    @Test
    @DisplayName("KB 已删除: 返回 failure")
    void should_ReturnFailure_When_KbDeleted() {
        createKb("kb-deleted", "DeletedKB", KnowledgeStatus.DELETED);

        IngestResult result = ingestor.ingestDocument(
                "kb-deleted", "doc-x", "Name", "content",
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 512, 64);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("deleted");
    }

    @Test
    @DisplayName("content 为空: 返回 failure")
    void should_ReturnFailure_When_ContentEmpty() {
        createKb("kb-empty", "EmptyKB", KnowledgeStatus.READY);

        IngestResult result = ingestor.ingestDocument(
                "kb-empty", "doc-x", "Name", "",
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 512, 64);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("empty");
    }

    @Test
    @DisplayName("幂等 re-ingest: 同 docId 重新导入, 旧 chunks 被删除, 新 chunks 创建")
    void should_BeIdempotent_When_ReingestSameDocId() {
        KnowledgeBase kb = createKb("kb-idem", "IdemKB", KnowledgeStatus.READY);

        // First ingest
        String content1 = "a".repeat(100);
        IngestResult result1 = ingestor.ingestDocument(
                "kb-idem", "doc-idem", "Doc", content1,
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 10, 2);
        assertThat(result1.isSuccess()).isTrue();
        int firstChunkCount = result1.getChunkCount();

        // Second ingest with different content (different chunk count)
        String content2 = "b".repeat(50);
        IngestResult result2 = ingestor.ingestDocument(
                "kb-idem", "doc-idem", "Doc", content2,
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 10, 2);
        assertThat(result2.isSuccess()).isTrue();

        // Verify old chunks replaced (total chunks in DB = new chunk count, not 2x)
        List<DocumentChunk> remaining = chunkRepository.findByKbIdAndDocId("kb-idem", "doc-idem");
        assertThat(remaining).hasSize(result2.getChunkCount());
        assertThat(remaining).isNotEqualTo(firstChunkCount); // content2 != content1

        // Verify only 1 KnowledgeDocument (not 2)
        List<KnowledgeDocument> docs = documentRepository.findByKbId("kb-idem");
        assertThat(docs).hasSize(1);

        // Verify KB docCount still 1 (not 2)
        KnowledgeBase updatedKb = kbRepository.findByKbId("kb-idem").orElseThrow();
        assertThat(updatedKb.getDocCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Markdown 文档: 先 parse 去标记再 split")
    void should_ParseMarkdown_When_TypeMarkdown() {
        createKb("kb-md", "MdKB", KnowledgeStatus.READY);
        String markdown = "# Title\n**bold** content here for testing chunk splitting logic";

        IngestResult result = ingestor.ingestDocument(
                "kb-md", "doc-md", "MdDoc", markdown,
                DocumentType.MARKDOWN, ChunkStrategyType.TOKEN, 512, 64);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChunkCount()).isGreaterThanOrEqualTo(1);

        // Verify chunk content has markdown stripped
        List<DocumentChunk> chunks = chunkRepository.findByKbIdAndDocId("kb-md", "doc-md");
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getContent()).doesNotContain("#");
        assertThat(chunks.get(0).getContent()).doesNotContain("**");
    }

    @Test
    @DisplayName("chunk orderIndex 从 0 递增")
    void should_SetOrderIndex_When_MultipleChunks() {
        createKb("kb-order", "OrderKB", KnowledgeStatus.READY);
        String content = "a".repeat(100);

        IngestResult result = ingestor.ingestDocument(
                "kb-order", "doc-order", "Doc", content,
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 10, 2);

        assertThat(result.isSuccess()).isTrue();
        List<DocumentChunk> chunks = chunkRepository.findByKbIdAndDocId("kb-order", "doc-order");
        // orderIndex may not be persisted in order, so sort by orderIndex then verify
        chunks.sort(java.util.Comparator.comparingInt(DocumentChunk::getOrderIndex));
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getOrderIndex()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("docId 为空时自动生成")
    void should_GenerateDocId_When_DocIdNull() {
        createKb("kb-autoid", "AutoIdKB", KnowledgeStatus.READY);

        IngestResult result = ingestor.ingestDocument(
                "kb-autoid", null, "AutoDoc", "some content",
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 512, 64);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDocId()).startsWith("doc-");
        assertThat(documentRepository.findByDocId(result.getDocId())).isPresent();
    }

    @Test
    @DisplayName("CREATING 状态 KB 导入后转为 UPDATING")
    void should_TransitionToUpdating_When_IngestingIntoCreatingKb() {
        createKb("kb-creating", "CreatingKB", KnowledgeStatus.CREATING);

        IngestResult result = ingestor.ingestDocument(
                "kb-creating", "doc-1", "Doc", "content",
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 512, 64);

        assertThat(result.isSuccess()).isTrue();
        KnowledgeBase kb = kbRepository.findByKbId("kb-creating").orElseThrow();
        assertThat(kb.getStatus()).isEqualTo(KnowledgeStatus.UPDATING);
    }

    @Test
    @DisplayName("多文档导入同一 KB: docCount 累加, chunkCount 累加")
    void should_AccumulateStats_When_MultipleDocsInSameKb() {
        createKb("kb-multi", "MultiKB", KnowledgeStatus.READY);

        ingestor.ingestDocument("kb-multi", "d1", "Doc1", "a".repeat(100),
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 10, 2);
        ingestor.ingestDocument("kb-multi", "d2", "Doc2", "b".repeat(80),
                DocumentType.TEXT, ChunkStrategyType.TOKEN, 10, 2);

        KnowledgeBase kb = kbRepository.findByKbId("kb-multi").orElseThrow();
        assertThat(kb.getDocCount()).isEqualTo(2);
        // chunkCount should be sum of both docs' chunks
        List<DocumentChunk> all = chunkRepository.findByKbId("kb-multi");
        assertThat(kb.getChunkCount()).isEqualTo(all.size());
    }
}
