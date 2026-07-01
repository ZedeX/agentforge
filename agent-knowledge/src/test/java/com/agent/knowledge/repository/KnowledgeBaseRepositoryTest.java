package com.agent.knowledge.repository;

import com.agent.knowledge.enums.KnowledgeStatus;
import com.agent.knowledge.model.KnowledgeBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KnowledgeBaseRepository JPA integration tests (Plan 08 T8).
 *
 * <p>Uses @DataJpaTest with H2 in-memory database (MODE=MySQL) to verify entity
 * mapping, default field values, @PrePersist timestamp auto-fill and repository
 * query methods.</p>
 */
@DisplayName("KnowledgeBaseRepository JPA 仓储测试")
@DataJpaTest
@ActiveProfiles("test")
class KnowledgeBaseRepositoryTest {

    @Autowired
    private KnowledgeBaseRepository repository;

    private KnowledgeBase buildKb(String kbId, String name, KnowledgeStatus status) {
        KnowledgeBase kb = new KnowledgeBase(kbId, name);
        kb.setDescription("Knowledge base " + name);
        kb.setStatus(status);
        return kb;
    }

    @Test
    @DisplayName("findByKbId 按 kbId 精确查询返回知识库")
    void should_FindByKbId_When_Exists() {
        repository.save(buildKb("kb-001", "CodeKB", KnowledgeStatus.READY));

        Optional<KnowledgeBase> found = repository.findByKbId("kb-001");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("CodeKB");
        assertThat(found.get().getDescription()).isEqualTo("Knowledge base CodeKB");
    }

    @Test
    @DisplayName("findByKbId 查询不存在的 kbId 返回 empty")
    void should_ReturnEmpty_When_KbIdNotFound() {
        Optional<KnowledgeBase> found = repository.findByKbId("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByKbId 检查 kbId 存在性")
    void should_CheckExistence_When_ExistsByKbId() {
        repository.save(buildKb("kb-002", "ReviewKB", KnowledgeStatus.CREATING));

        assertThat(repository.existsByKbId("kb-002")).isTrue();
        assertThat(repository.existsByKbId("missing-id")).isFalse();
    }

    @Test
    @DisplayName("findByStatus 按状态过滤知识库")
    void should_FilterByStatus_When_FindByStatus() {
        repository.save(buildKb("k1", "K1", KnowledgeStatus.CREATING));
        repository.save(buildKb("k2", "K2", KnowledgeStatus.READY));
        repository.save(buildKb("k3", "K3", KnowledgeStatus.READY));

        List<KnowledgeBase> ready = repository.findByStatus(KnowledgeStatus.READY);

        assertThat(ready).hasSize(2);
        assertThat(ready).extracting(KnowledgeBase::getKbId)
                .containsExactlyInAnyOrder("k2", "k3");
    }

    @Test
    @DisplayName("kb_id 唯一约束: 重复插入抛异常")
    void should_Throw_When_DuplicateKbIdInserted() {
        repository.save(buildKb("dup-id", "First", KnowledgeStatus.CREATING));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                repository.save(buildKb("dup-id", "Second", KnowledgeStatus.CREATING))
        ).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("@PrePersist 自动填充 createdAt 和 updatedAt")
    void should_AutoFillTimestamps_When_Saved() {
        KnowledgeBase saved = repository.save(buildKb("ts-001", "TS", KnowledgeStatus.READY));

        assertThat(saved.getCreatedAt()).isGreaterThan(0);
        assertThat(saved.getUpdatedAt()).isGreaterThan(0);
        assertThat(saved.getUpdatedAt()).isGreaterThanOrEqualTo(saved.getCreatedAt());
    }

    @Test
    @DisplayName("默认值: status=CREATING / dimension=1024 / docCount=0 / chunkCount=0 / embeddingModel=bge-large-zh-v1.5")
    void should_HaveDefaultValues_When_SavedWithMinimalFields() {
        KnowledgeBase kb = new KnowledgeBase("default-001", "DefaultKB");
        // 不显式设置 status / dimension / docCount / chunkCount / embeddingModel
        KnowledgeBase saved = repository.save(kb);

        assertThat(saved.getStatus()).isEqualTo(KnowledgeStatus.CREATING);
        assertThat(saved.getDimension()).isEqualTo(1024);
        assertThat(saved.getDocCount()).isEqualTo(0);
        assertThat(saved.getChunkCount()).isEqualTo(0);
        assertThat(saved.getEmbeddingModel()).isEqualTo("bge-large-zh-v1.5");
    }

    @Test
    @DisplayName("deleteByKbId 按 kbId 删除并返回删除数")
    void should_DeleteByKbId_When_Exists() {
        repository.save(buildKb("del-001", "DelKB", KnowledgeStatus.CREATING));

        long deleted = repository.deleteByKbId("del-001");

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.existsByKbId("del-001")).isFalse();
    }
}
