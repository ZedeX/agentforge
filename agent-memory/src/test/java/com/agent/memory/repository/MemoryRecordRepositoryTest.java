package com.agent.memory.repository;

import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.enums.TaskOutcome;
import com.agent.memory.model.MemoryRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MemoryRecordRepository tests (Plan 03 T2).
 *
 * <p>@DataJpaTest + H2 MySQL 模式，验证 CRUD + 自定义查询 + 唯一约束。
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("MemoryRecordRepository 持久化测试")
class MemoryRecordRepositoryTest {

    @Autowired
    private MemoryRecordRepository repository;

    private MemoryRecord buildRecord(String memoryId, String tenantId, MemoryStatus status) {
        MemoryRecord record = new MemoryRecord(memoryId, MemoryType.EPISODIC, "test content");
        record.setTenantId(tenantId);
        record.setStatus(status);
        record.setImportanceScore(0.5);
        record.setTopic("order");
        record.setContentHash("sha256_" + memoryId);
        return record;
    }

    @Test
    @DisplayName("should save and find by id with all fields")
    void should_SaveAndFindById_When_AllFieldsPopulated() {
        MemoryRecord record = buildRecord("mem-001", "tenant-A", MemoryStatus.ACTIVE);
        record.setUserId("user-001");
        record.setSummary("distilled summary");
        record.setKeywords("[\"keyword1\",\"keyword2\"]");
        record.setSourceTaskId("tk-001");
        record.setOutcome(TaskOutcome.SUCCESS);
        record.setImportanceLevel("MEDIUM");
        record.setVectorId("vec-001");
        record.setParentMemoryId("parent-001");
        record.setChildMemoryIds("[\"child-001\"]");
        record.setTtlExpireAt(Instant.now().plusSeconds(86400));
        record.setDistillCount(2);
        record.setRecallCount(5);
        record.setLastRecalledAt(Instant.now());
        record.setMetadata("{\"key\":\"value\"}");

        MemoryRecord saved = repository.save(record);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<MemoryRecord> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getMemoryId()).isEqualTo("mem-001");
        assertThat(found.get().getTenantId()).isEqualTo("tenant-A");
        assertThat(found.get().getUserId()).isEqualTo("user-001");
        assertThat(found.get().getType()).isEqualTo(MemoryType.EPISODIC);
        assertThat(found.get().getStatus()).isEqualTo(MemoryStatus.ACTIVE);
        assertThat(found.get().getContent()).isEqualTo("test content");
        assertThat(found.get().getSummary()).isEqualTo("distilled summary");
        assertThat(found.get().getTopic()).isEqualTo("order");
        assertThat(found.get().getImportanceScore()).isEqualTo(0.5);
        assertThat(found.get().getImportanceLevel()).isEqualTo("MEDIUM");
        assertThat(found.get().getContentHash()).isEqualTo("sha256_mem-001");
        assertThat(found.get().getVectorId()).isEqualTo("vec-001");
        assertThat(found.get().getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(found.get().getRecallCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("should find by memoryId")
    void should_FindByMemoryId_When_BusinessIdExists() {
        repository.save(buildRecord("mem-002", "tenant-A", MemoryStatus.ACTIVE));

        Optional<MemoryRecord> found = repository.findByMemoryId("mem-002");
        assertThat(found).isPresent();
        assertThat(found.get().getTenantId()).isEqualTo("tenant-A");
    }

    @Test
    @DisplayName("should find by tenantId and status")
    void should_FindByTenantIdAndStatus_When_Filtering() {
        repository.save(buildRecord("mem-003", "tenant-A", MemoryStatus.ACTIVE));
        repository.save(buildRecord("mem-004", "tenant-A", MemoryStatus.ACTIVE));
        repository.save(buildRecord("mem-005", "tenant-A", MemoryStatus.ARCHIVED));
        repository.save(buildRecord("mem-006", "tenant-B", MemoryStatus.ACTIVE));

        List<MemoryRecord> results = repository.findByTenantIdAndStatus("tenant-A", MemoryStatus.ACTIVE);
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(r -> {
            assertThat(r.getTenantId()).isEqualTo("tenant-A");
            assertThat(r.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
        });
    }

    @Test
    @DisplayName("should find by topic")
    void should_FindByTopic_When_TopicMatches() {
        MemoryRecord r1 = buildRecord("mem-007", "tenant-A", MemoryStatus.ACTIVE);
        r1.setTopic("order");
        MemoryRecord r2 = buildRecord("mem-008", "tenant-A", MemoryStatus.ACTIVE);
        r2.setTopic("billing");
        repository.save(r1);
        repository.save(r2);

        List<MemoryRecord> results = repository.findByTopic("order");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMemoryId()).isEqualTo("mem-007");
    }

    @Test
    @DisplayName("should find expired records before given time")
    void should_FindExpiredBefore_When_TtlExpired() {
        MemoryRecord r1 = buildRecord("mem-009", "tenant-A", MemoryStatus.ACTIVE);
        r1.setTtlExpireAt(Instant.now().minusSeconds(3600));
        MemoryRecord r2 = buildRecord("mem-010", "tenant-A", MemoryStatus.ACTIVE);
        r2.setTtlExpireAt(Instant.now().plusSeconds(3600));
        repository.save(r1);
        repository.save(r2);

        Page<MemoryRecord> results = repository.findByStatusAndTtlExpireAtBefore(
                MemoryStatus.ACTIVE, Instant.now(), PageRequest.of(0, 10));
        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getMemoryId()).isEqualTo("mem-009");
    }

    @Test
    @DisplayName("should count by tenantId and status")
    void should_CountByTenantIdAndStatus_When_Counting() {
        repository.save(buildRecord("mem-011", "tenant-A", MemoryStatus.ACTIVE));
        repository.save(buildRecord("mem-012", "tenant-A", MemoryStatus.ACTIVE));
        repository.save(buildRecord("mem-013", "tenant-A", MemoryStatus.RAW));

        long count = repository.countByTenantIdAndStatus("tenant-A", MemoryStatus.ACTIVE);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("should find by contentHash")
    void should_FindByContentHash_When_HashMatches() {
        MemoryRecord record = buildRecord("mem-014", "tenant-A", MemoryStatus.ACTIVE);
        record.setContentHash("hash-abc-123");
        repository.save(record);

        Optional<MemoryRecord> found = repository.findByContentHash("hash-abc-123");
        assertThat(found).isPresent();
        assertThat(found.get().getMemoryId()).isEqualTo("mem-014");
    }

    @Test
    @DisplayName("should throw when memoryId is duplicated")
    void should_Throw_When_MemoryIdDuplicated() {
        repository.save(buildRecord("mem-dup", "tenant-A", MemoryStatus.ACTIVE));

        assertThatThrownBy(() -> repository.save(buildRecord("mem-dup", "tenant-B", MemoryStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should set createdAt on persist via @PrePersist")
    void should_SetCreatedAt_When_Persisted() {
        MemoryRecord record = buildRecord("mem-timestamp", "tenant-A", MemoryStatus.RAW);
        MemoryRecord saved = repository.saveAndFlush(record);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
