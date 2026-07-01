package com.agent.knowledge.repository;

import com.agent.knowledge.model.KnowledgeVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KnowledgeVersionRepository JPA integration tests (Plan 08 T8).
 *
 * <p>Uses @DataJpaTest with H2 in-memory database (MODE=MySQL) to verify entity
 * mapping (including refactored mutable fields from final), version history
 * ordering, latest version probe and FIFO count.</p>
 */
@DisplayName("KnowledgeVersionRepository JPA 仓储测试")
@DataJpaTest
@ActiveProfiles("test")
class KnowledgeVersionRepositoryTest {

    @Autowired
    private KnowledgeVersionRepository repository;

    private KnowledgeVersion buildVersion(String versionId, String kbId, int version, String changeLog) {
        return new KnowledgeVersion(
                versionId, kbId, version,
                "{\"kbId\":\"" + kbId + "\",\"version\":" + version + "}",
                changeLog,
                System.currentTimeMillis()
        );
    }

    @Test
    @DisplayName("findByVersionId 按 versionId 精确查询返回版本快照")
    void should_FindByVersionId_When_Exists() {
        repository.save(buildVersion("ver-001", "kb-1", 1, "initial"));

        Optional<KnowledgeVersion> found = repository.findByVersionId("ver-001");

        assertThat(found).isPresent();
        assertThat(found.get().getKbId()).isEqualTo("kb-1");
        assertThat(found.get().getVersion()).isEqualTo(1);
        assertThat(found.get().getChangeLog()).isEqualTo("initial");
        assertThat(found.get().getSnapshot()).contains("kb-1");
    }

    @Test
    @DisplayName("findByVersionId 查询不存在的 versionId 返回 empty")
    void should_ReturnEmpty_When_VersionIdNotFound() {
        Optional<KnowledgeVersion> found = repository.findByVersionId("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByKbIdOrderByVersionDesc 按版本号降序返回历史")
    void should_FindByKbIdOrderByVersionDesc_When_MultipleVersions() {
        repository.save(buildVersion("v1", "kb-hist", 1, "v1"));
        repository.save(buildVersion("v3", "kb-hist", 3, "v3"));
        repository.save(buildVersion("v2", "kb-hist", 2, "v2"));

        List<KnowledgeVersion> history = repository.findByKbIdOrderByVersionDesc("kb-hist");

        assertThat(history).hasSize(3);
        assertThat(history.get(0).getVersion()).isEqualTo(3);
        assertThat(history.get(1).getVersion()).isEqualTo(2);
        assertThat(history.get(2).getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("findTopByKbIdOrderByVersionDesc 返回最新版本")
    void should_FindTopByKbIdOrderByVersionDesc_When_LatestVersion() {
        repository.save(buildVersion("v1", "kb-latest", 1, "v1"));
        repository.save(buildVersion("v2", "kb-latest", 2, "v2"));
        repository.save(buildVersion("v3", "kb-latest", 3, "v3"));

        Optional<KnowledgeVersion> latest = repository.findTopByKbIdOrderByVersionDesc("kb-latest");

        assertThat(latest).isPresent();
        assertThat(latest.get().getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("countByKbId 统计知识库版本数")
    void should_CountByKbId_When_MultipleVersions() {
        repository.save(buildVersion("v1", "kb-count", 1, "v1"));
        repository.save(buildVersion("v2", "kb-count", 2, "v2"));
        repository.save(buildVersion("v1-other", "kb-other", 1, "v1"));

        long count = repository.countByKbId("kb-count");

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("version_id 唯一约束: 重复插入抛异常")
    void should_Throw_When_DuplicateVersionIdInserted() {
        repository.save(buildVersion("dup-id", "kb-1", 1, "first"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                repository.save(buildVersion("dup-id", "kb-2", 2, "second"))
        ).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("@PrePersist 自动填充 createdAt (当未显式设置时)")
    void should_AutoFillTimestamps_When_SavedWithZeroCreatedAt() {
        KnowledgeVersion version = new KnowledgeVersion(
                "ts-001", "kb-1", 1, "{}", "test", 0L);
        KnowledgeVersion saved = repository.save(version);

        assertThat(saved.getCreatedAt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("deleteByKbId 按 kbId 批量删除版本快照 (级联清理)")
    void should_DeleteByKbId_When_CascadeDelete() {
        repository.save(buildVersion("v1", "kb-del", 1, "v1"));
        repository.save(buildVersion("v2", "kb-del", 2, "v2"));
        repository.save(buildVersion("v3", "kb-keep", 1, "v1"));

        long deleted = repository.deleteByKbId("kb-del");

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.countByKbId("kb-del")).isEqualTo(0);
        assertThat(repository.existsByVersionId("v3")).isTrue();
    }
}
