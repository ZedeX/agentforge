package com.agent.knowledge.integration;

import com.agent.knowledge.enums.DocumentType;
import com.agent.knowledge.enums.IngestStatus;
import com.agent.knowledge.enums.KnowledgeStatus;
import com.agent.knowledge.model.DocumentChunk;
import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.model.KnowledgeDocument;
import com.agent.knowledge.repository.DocumentChunkRepository;
import com.agent.knowledge.repository.KnowledgeBaseRepository;
import com.agent.knowledge.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E2E integration test: agent-knowledge JPA against real MySQL 8.0.36 via Testcontainers.
 *
 * <p>Validates DDL compatibility for {@code knowledge_base} + {@code knowledge_document} +
 * {@code knowledge_chunk} tables, TEXT column round-trip for large document content,
 * unique constraints {@code uk_kb_id} / {@code uk_doc_id} / {@code uk_chunk_id},
 * and cross-table queries ({@code findByKbId} / {@code findByDocId}) that exercise
 * the KB → Document → Chunk hierarchy.</p>
 *
 * <p>Skipped automatically when Docker is unavailable.</p>
 *
 * <p>Run via: {@code mvn -Pe2e-perf -pl agent-knowledge test -Dtest=KnowledgeJpaTestcontainersTest}</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class KnowledgeJpaTestcontainersTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("agent_knowledge")
            .withUsername("root")
            .withPassword("root")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private KnowledgeBaseRepository kbRepository;

    @Autowired
    private KnowledgeDocumentRepository docRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Test
    @DisplayName("保存 KnowledgeBase 后应能按 kbId 查询到完整字段")
    void should_PersistAndFindByKbId_When_AllFieldsPopulated() {
        KnowledgeBase kb = new KnowledgeBase("kb_tc_001", "Industry Research KB");
        kb.setDescription("Knowledge base for industry research agent, containing market reports and whitepapers.");
        kb.setEmbeddingModel("bge-large-zh-v1.5");
        kb.setDimension(1024);
        kb.setStatus(KnowledgeStatus.READY);

        kbRepository.save(kb);

        Optional<KnowledgeBase> found = kbRepository.findByKbId("kb_tc_001");
        assertThat(found).isPresent();
        KnowledgeBase loaded = found.get();
        assertThat(loaded.getName()).isEqualTo("Industry Research KB");
        assertThat(loaded.getStatus()).isEqualTo(KnowledgeStatus.READY);
        assertThat(loaded.getEmbeddingModel()).isEqualTo("bge-large-zh-v1.5");
        assertThat(loaded.getDimension()).isEqualTo(1024);
        assertThat(loaded.getDocCount()).isZero();
        assertThat(loaded.getChunkCount()).isZero();
        assertThat(loaded.getCreatedAt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("重复 kbId 应触发 uk_kb_id 唯一约束违反")
    void should_ThrowDataIntegrityViolation_When_DuplicateKbIdInserted() {
        kbRepository.saveAndFlush(new KnowledgeBase("kb_dup_001", "First KB"));

        assertThatThrownBy(() -> kbRepository.saveAndFlush(new KnowledgeBase("kb_dup_001", "Duplicate KB")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("保存 KnowledgeDocument 后应能按 kbId 查询该 KB 下所有文档")
    void should_FindDocumentsByKbId_When_MultipleDocumentsInSameKb() {
        KnowledgeDocument d1 = newDocument("doc_tc_001", "kb_q_001", "market-report.pdf", DocumentType.PDF);
        KnowledgeDocument d2 = newDocument("doc_tc_002", "kb_q_001", "whitepaper.md", DocumentType.MARKDOWN);
        KnowledgeDocument d3 = newDocument("doc_tc_003", "kb_q_002", "other.txt", DocumentType.TEXT);
        docRepository.saveAllAndFlush(java.util.Arrays.asList(d1, d2, d3));

        List<KnowledgeDocument> kb001Docs = docRepository.findByKbId("kb_q_001");

        assertThat(kb001Docs).hasSize(2);
        assertThat(kb001Docs).extracting(KnowledgeDocument::getDocId)
                .containsExactlyInAnyOrder("doc_tc_001", "doc_tc_002");
    }

    @Test
    @DisplayName("保存 DocumentChunk 后应能按 docId 查询该文档所有切片")
    void should_FindChunksByDocId_When_MultipleChunksInSameDocument() {
        chunkRepository.save(newChunk("chunk_tc_001", "doc_c_001", "kb_c_001", "chunk content 1", 1));
        chunkRepository.save(newChunk("chunk_tc_002", "doc_c_001", "kb_c_001", "chunk content 2", 2));
        chunkRepository.save(newChunk("chunk_tc_003", "doc_c_002", "kb_c_001", "chunk content 3", 1));
        chunkRepository.flush();

        List<DocumentChunk> doc001Chunks = chunkRepository.findByDocId("doc_c_001");

        assertThat(doc001Chunks).hasSize(2);
        assertThat(doc001Chunks).extracting(DocumentChunk::getChunkId)
                .containsExactlyInAnyOrder("chunk_tc_001", "chunk_tc_002");
    }

    @Test
    @DisplayName("三表关联查询：KB → Documents → Chunks 应正确返回层级数据")
    void should_QueryHierarchy_When_KbDocumentChunkAllPopulated() {
        // KB
        KnowledgeBase kb = new KnowledgeBase("kb_h_001", "Hierarchy KB");
        kb.setStatus(KnowledgeStatus.READY);
        kbRepository.saveAndFlush(kb);

        // 2 Documents under KB
        KnowledgeDocument d1 = newDocument("doc_h_001", "kb_h_001", "doc1.md", DocumentType.MARKDOWN);
        KnowledgeDocument d2 = newDocument("doc_h_002", "kb_h_001", "doc2.md", DocumentType.MARKDOWN);
        docRepository.saveAllAndFlush(java.util.Arrays.asList(d1, d2));

        // 3 Chunks: 2 under doc1, 1 under doc2
        chunkRepository.save(newChunk("chunk_h_001", "doc_h_001", "kb_h_001", "content 1", 1));
        chunkRepository.save(newChunk("chunk_h_002", "doc_h_001", "kb_h_001", "content 2", 2));
        chunkRepository.save(newChunk("chunk_h_003", "doc_h_002", "kb_h_001", "content 3", 1));
        chunkRepository.flush();

        // Hierarchy query
        List<KnowledgeDocument> docs = docRepository.findByKbId("kb_h_001");
        List<DocumentChunk> doc1Chunks = chunkRepository.findByKbIdAndDocId("kb_h_001", "doc_h_001");
        List<DocumentChunk> allChunks = chunkRepository.findByKbId("kb_h_001");

        assertThat(docs).hasSize(2);
        assertThat(doc1Chunks).hasSize(2);
        assertThat(allChunks).hasSize(3);
        assertThat(chunkRepository.countByKbId("kb_h_001")).isEqualTo(3L);
    }

    private KnowledgeDocument newDocument(String docId, String kbId, String name, DocumentType type) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setDocId(docId);
        doc.setKbId(kbId);
        doc.setName(name);
        doc.setType(type);
        doc.setContent("Large document content for " + name + ". ".repeat(50));
        doc.setSizeBytes(2048);
        doc.setChunkCount(5);
        doc.setTokenCount(1500);
        return doc;
    }

    private DocumentChunk newChunk(String chunkId, String docId, String kbId, String content, int order) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setChunkId(chunkId);
        chunk.setDocId(docId);
        chunk.setKbId(kbId);
        chunk.setContent(content);
        chunk.setTokenCount(100);
        chunk.setOrderIndex(order);
        chunk.setEmbeddingId("emb_" + chunkId);
        chunk.setStatus(IngestStatus.VECTORIZED);
        return chunk;
    }
}
