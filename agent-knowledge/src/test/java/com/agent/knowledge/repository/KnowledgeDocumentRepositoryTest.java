package com.agent.knowledge.repository;

import com.agent.knowledge.enums.DocumentType;
import com.agent.knowledge.model.KnowledgeDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KnowledgeDocumentRepository JPA integration tests (Plan 08 T8).
 *
 * <p>Uses @DataJpaTest with H2 in-memory database (MODE=MySQL) to verify entity
 * mapping, DocumentType enum persistence, kbId-based filtering and bulk delete.</p>
 */
@DisplayName("KnowledgeDocumentRepository JPA 仓储测试")
@DataJpaTest
@ActiveProfiles("test")
class KnowledgeDocumentRepositoryTest {

    @Autowired
    private KnowledgeDocumentRepository repository;

    private KnowledgeDocument buildDoc(String docId, String kbId, String name, DocumentType type) {
        KnowledgeDocument doc = new KnowledgeDocument(docId, kbId, name);
        doc.setType(type);
        doc.setContent("Sample content for " + name);
        doc.setSizeBytes(100);
        return doc;
    }

    @Test
    @DisplayName("findByDocId 按 docId 精确查询返回文档")
    void should_FindByDocId_When_Exists() {
        repository.save(buildDoc("doc-001", "kb-1", "Readme", DocumentType.MARKDOWN));

        Optional<KnowledgeDocument> found = repository.findByDocId("doc-001");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Readme");
        assertThat(found.get().getType()).isEqualTo(DocumentType.MARKDOWN);
        assertThat(found.get().getContent()).isEqualTo("Sample content for Readme");
    }

    @Test
    @DisplayName("findByDocId 查询不存在的 docId 返回 empty")
    void should_ReturnEmpty_When_DocIdNotFound() {
        Optional<KnowledgeDocument> found = repository.findByDocId("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByKbId 按 kbId 查询返回同知识库下所有文档")
    void should_FindByKbId_When_MultipleDocsInSameKb() {
        repository.save(buildDoc("d1", "kb-shared", "Doc1", DocumentType.TEXT));
        repository.save(buildDoc("d2", "kb-shared", "Doc2", DocumentType.HTML));
        repository.save(buildDoc("d3", "kb-other", "Doc3", DocumentType.TEXT));

        List<KnowledgeDocument> docs = repository.findByKbId("kb-shared");

        assertThat(docs).hasSize(2);
        assertThat(docs).extracting(KnowledgeDocument::getDocId)
                .containsExactlyInAnyOrder("d1", "d2");
    }

    @Test
    @DisplayName("existsByDocId 检查 docId 存在性")
    void should_CheckExistence_When_ExistsByDocId() {
        repository.save(buildDoc("doc-002", "kb-1", "TestDoc", DocumentType.TEXT));

        assertThat(repository.existsByDocId("doc-002")).isTrue();
        assertThat(repository.existsByDocId("missing")).isFalse();
    }

    @Test
    @DisplayName("doc_id 唯一约束: 重复插入抛异常")
    void should_Throw_When_DuplicateDocIdInserted() {
        repository.save(buildDoc("dup-id", "kb-1", "First", DocumentType.TEXT));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                repository.save(buildDoc("dup-id", "kb-2", "Second", DocumentType.TEXT))
        ).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("@PrePersist 自动填充 createdAt")
    void should_AutoFillTimestamps_When_Saved() {
        KnowledgeDocument saved = repository.save(buildDoc("ts-001", "kb-1", "TS", DocumentType.TEXT));

        assertThat(saved.getCreatedAt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("DocumentType 枚举持久化往返: 保存后读回类型一致")
    void should_RoundTripDocumentType_When_SavedAndReadBack() {
        repository.save(buildDoc("enum-001", "kb-1", "HtmlDoc", DocumentType.HTML));

        KnowledgeDocument found = repository.findByDocId("enum-001").orElseThrow();

        assertThat(found.getType()).isEqualTo(DocumentType.HTML);
    }

    @Test
    @DisplayName("deleteByKbId 按 kbId 批量删除文档")
    void should_DeleteByKbId_When_CascadeDelete() {
        repository.save(buildDoc("d1", "kb-del", "Doc1", DocumentType.TEXT));
        repository.save(buildDoc("d2", "kb-del", "Doc2", DocumentType.TEXT));
        repository.save(buildDoc("d3", "kb-keep", "Doc3", DocumentType.TEXT));

        long deleted = repository.deleteByKbId("kb-del");

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findByKbId("kb-del")).isEmpty();
        assertThat(repository.existsByDocId("d3")).isTrue();
    }
}
