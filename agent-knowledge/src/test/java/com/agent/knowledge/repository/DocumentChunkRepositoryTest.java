package com.agent.knowledge.repository;

import com.agent.knowledge.enums.IngestStatus;
import com.agent.knowledge.model.DocumentChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentChunkRepository JPA integration tests (Plan 08 T8).
 *
 * <p>Uses @DataJpaTest with H2 in-memory database (MODE=MySQL) to verify entity
 * mapping, IngestStatus enum persistence, kbId+docId combined query, count and
 * idempotent re-ingest delete.</p>
 */
@DisplayName("DocumentChunkRepository JPA 仓储测试")
@DataJpaTest
@ActiveProfiles("test")
class DocumentChunkRepositoryTest {

    @Autowired
    private DocumentChunkRepository repository;

    private DocumentChunk buildChunk(String chunkId, String docId, String kbId, String content, int orderIndex) {
        DocumentChunk chunk = new DocumentChunk(chunkId, docId, kbId, content);
        chunk.setOrderIndex(orderIndex);
        chunk.setTokenCount(content.length() / 4);
        return chunk;
    }

    @Test
    @DisplayName("findByChunkId 按 chunkId 精确查询返回切片")
    void should_FindByChunkId_When_Exists() {
        repository.save(buildChunk("chunk-001", "doc-1", "kb-1", "Hello world", 0));

        Optional<DocumentChunk> found = repository.findByChunkId("chunk-001");

        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("Hello world");
        assertThat(found.get().getOrderIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("findByKbIdAndDocId 按 kbId+docId 查询返回同文档全部切片")
    void should_FindByKbIdAndDocId_When_MultipleChunksInSameDoc() {
        repository.save(buildChunk("c1", "doc-a", "kb-1", "chunk1", 0));
        repository.save(buildChunk("c2", "doc-a", "kb-1", "chunk2", 1));
        repository.save(buildChunk("c3", "doc-a", "kb-1", "chunk3", 2));
        repository.save(buildChunk("c4", "doc-b", "kb-1", "chunk4", 0));

        List<DocumentChunk> chunks = repository.findByKbIdAndDocId("kb-1", "doc-a");

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(DocumentChunk::getChunkId)
                .containsExactlyInAnyOrder("c1", "c2", "c3");
    }

    @Test
    @DisplayName("countByKbId 统计知识库下切片总数")
    void should_CountByKbId_When_MultipleChunksInSameKb() {
        repository.save(buildChunk("c1", "doc-a", "kb-count", "x", 0));
        repository.save(buildChunk("c2", "doc-a", "kb-count", "y", 1));
        repository.save(buildChunk("c3", "doc-b", "kb-count", "z", 0));
        repository.save(buildChunk("c4", "doc-c", "kb-other", "w", 0));

        long count = repository.countByKbId("kb-count");

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("deleteByKbIdAndDocId 幂等重新导入: 删除旧切片后重新分块")
    void should_DeleteByKbIdAndDocId_When_IdempotentReingest() {
        repository.save(buildChunk("c1", "doc-reingest", "kb-1", "old1", 0));
        repository.save(buildChunk("c2", "doc-reingest", "kb-1", "old2", 1));
        repository.save(buildChunk("c3", "doc-keep", "kb-1", "keep", 0));

        long deleted = repository.deleteByKbIdAndDocId("kb-1", "doc-reingest");

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findByKbIdAndDocId("kb-1", "doc-reingest")).isEmpty();
        assertThat(repository.existsByChunkId("c3")).isTrue();
    }

    @Test
    @DisplayName("chunk_id 唯一约束: 重复插入抛异常")
    void should_Throw_When_DuplicateChunkIdInserted() {
        repository.save(buildChunk("dup-id", "doc-1", "kb-1", "first", 0));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                repository.save(buildChunk("dup-id", "doc-2", "kb-2", "second", 0))
        ).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("@PrePersist 自动填充 createdAt")
    void should_AutoFillTimestamps_When_Saved() {
        DocumentChunk saved = repository.save(buildChunk("ts-001", "doc-1", "kb-1", "ts", 0));

        assertThat(saved.getCreatedAt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("默认值: status=PENDING (待向量化)")
    void should_HaveDefaultStatus_When_SavedWithMinimalFields() {
        DocumentChunk chunk = new DocumentChunk("default-001", "doc-1", "kb-1", "content");
        DocumentChunk saved = repository.save(chunk);

        assertThat(saved.getStatus()).isEqualTo(IngestStatus.PENDING);
        assertThat(saved.getTokenCount()).isEqualTo(0);
        assertThat(saved.getOrderIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("IngestStatus 枚举持久化往返: 保存后读回状态一致")
    void should_RoundTripIngestStatus_When_SavedAndReadBack() {
        DocumentChunk chunk = buildChunk("enum-001", "doc-1", "kb-1", "vectorized content", 0);
        chunk.setStatus(IngestStatus.VECTORIZED);
        chunk.setEmbeddingId("milvus-pk-001");
        repository.save(chunk);

        DocumentChunk found = repository.findByChunkId("enum-001").orElseThrow();

        assertThat(found.getStatus()).isEqualTo(IngestStatus.VECTORIZED);
        assertThat(found.getEmbeddingId()).isEqualTo("milvus-pk-001");
    }

    @Test
    @DisplayName("deleteByKbId 按 kbId 批量删除切片 (级联清理)")
    void should_DeleteByKbId_When_CascadeDelete() {
        repository.save(buildChunk("c1", "doc-a", "kb-del", "x", 0));
        repository.save(buildChunk("c2", "doc-b", "kb-del", "y", 0));
        repository.save(buildChunk("c3", "doc-c", "kb-keep", "z", 0));

        long deleted = repository.deleteByKbId("kb-del");

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findByKbId("kb-del")).isEmpty();
        assertThat(repository.existsByChunkId("c3")).isTrue();
    }
}
