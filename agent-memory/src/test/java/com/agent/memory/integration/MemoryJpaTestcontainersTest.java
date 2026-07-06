package com.agent.memory.integration;

import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.repository.MemoryRecordRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E2E integration test: agent-memory JPA against real MySQL 8.0.36 via Testcontainers.
 *
 * <p>Validates DDL compatibility, {@code uk_memory_id} unique constraint,
 * {@code @Lob} content column round-trip with large text, {@code Instant} time
 * fields persistence, and composite index {@code idx_tenant_status} usage via
 * {@code findByTenantIdAndStatus} query.</p>
 *
 * <p>Skipped automatically when Docker is unavailable.</p>
 *
 * <p>Run via: {@code mvn -Pe2e-perf -pl agent-memory test -Dtest=MemoryJpaTestcontainersTest}</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemoryJpaTestcontainersTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("agent_memory")
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
    private MemoryRecordRepository repository;

    @Test
    @DisplayName("保存 MemoryRecord 后应能按 memoryId 查询到 @Lob content 完整内容")
    void should_PersistAndFindByMemoryId_When_LobContentStored() {
        // 8KB content — exercises @Lob column with real MySQL LONGTEXT
        StringBuilder large = new StringBuilder(8 * 1024);
        for (int i = 0; i < 8 * 1024; i++) {
            large.append('x');
        }
        String content = large.toString();

        MemoryRecord rec = new MemoryRecord();
        rec.setMemoryId("mem_tc_001");
        rec.setTenantId("tn_001");
        rec.setUserId("u_001");
        rec.setType(MemoryType.EPISODIC);
        rec.setStatus(MemoryStatus.ACTIVE);
        rec.setContent(content);
        rec.setSummary("short summary");
        rec.setTopic("performance-tuning");
        rec.setImportanceScore(0.85);
        rec.setImportanceLevel("HIGH");
        rec.setContentHash("sha256_" + content.hashCode());
        rec.setTtlExpireAt(Instant.now().plus(30, ChronoUnit.DAYS));

        repository.save(rec);

        Optional<MemoryRecord> found = repository.findByMemoryId("mem_tc_001");
        assertThat(found).isPresent();
        MemoryRecord loaded = found.get();
        assertThat(loaded.getContent()).isEqualTo(content);
        assertThat(loaded.getContent().length()).isEqualTo(8 * 1024);
        assertThat(loaded.getType()).isEqualTo(MemoryType.EPISODIC);
        assertThat(loaded.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
        assertThat(loaded.getImportanceScore()).isEqualTo(0.85);
        assertThat(loaded.getImportanceLevel()).isEqualTo("HIGH");
        assertThat(loaded.getTtlExpireAt()).isNotNull();
        // Instant round-trip loses nanosecond precision in MySQL datetime(6), truncate for compare
        assertThat(loaded.getTtlExpireAt().truncatedTo(ChronoUnit.SECONDS))
                .isEqualTo(rec.getTtlExpireAt().truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("重复 memoryId 应触发 uk_memory_id 唯一约束违反")
    void should_ThrowDataIntegrityViolation_When_DuplicateMemoryIdInserted() {
        MemoryRecord first = newRecord("mem_dup_001", "tn_001", MemoryStatus.ACTIVE);
        repository.saveAndFlush(first);

        MemoryRecord dup = newRecord("mem_dup_001", "tn_001", MemoryStatus.ACTIVE);

        assertThatThrownBy(() -> repository.saveAndFlush(dup))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("按 tenantId + status 查询应利用 idx_tenant_status 索引返回匹配记录")
    void should_FindByTenantIdAndStatus_When_CompositeIndexQueryIssued() {
        repository.save(newRecord("mem_q_001", "tn_A", MemoryStatus.ACTIVE));
        repository.save(newRecord("mem_q_002", "tn_A", MemoryStatus.ACTIVE));
        repository.save(newRecord("mem_q_003", "tn_A", MemoryStatus.ARCHIVED));
        repository.save(newRecord("mem_q_004", "tn_B", MemoryStatus.ACTIVE));

        List<MemoryRecord> tnA_active = repository.findByTenantIdAndStatus("tn_A", MemoryStatus.ACTIVE);

        assertThat(tnA_active).hasSize(2);
        assertThat(tnA_active).extracting(MemoryRecord::getMemoryId)
                .containsExactlyInAnyOrder("mem_q_001", "mem_q_002");
    }

    @Test
    @DisplayName("按 contentHash 查询应返回匹配记录（去重场景的代表性查询）")
    void should_FindByContentHash_When_DeduplicationLookupIssued() {
        // Spring Data findByContentHash returns Optional (single result);
        // dedup callers check existence via isPresent() before deciding to skip or merge.
        // Two distinct hashes here — verifies both hit and miss paths.
        repository.save(newRecordWithHash("mem_h_001", "tn_001", "hash_abc"));
        repository.save(newRecordWithHash("mem_h_002", "tn_001", "hash_xyz"));

        Optional<MemoryRecord> dupe = repository.findByContentHash("hash_abc");
        Optional<MemoryRecord> missing = repository.findByContentHash("hash_not_exist");

        assertThat(dupe).isPresent();
        assertThat(dupe.get().getContentHash()).isEqualTo("hash_abc");
        assertThat(dupe.get().getMemoryId()).isEqualTo("mem_h_001");
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("RAW → ACTIVE → DISTILLED → ARCHIVED 状态流转应可被 status 字段正确持久化")
    void should_PersistAllStatusValues_When_StateMachineTransitionsOccur() {
        for (MemoryStatus status : MemoryStatus.values()) {
            MemoryRecord rec = newRecord("mem_st_" + status.name(), "tn_sm", status);
            repository.saveAndFlush(rec);

            Optional<MemoryRecord> loaded = repository.findByMemoryId("mem_st_" + status.name());
            assertThat(loaded).isPresent();
            assertThat(loaded.get().getStatus()).isEqualTo(status);
        }
    }

    private MemoryRecord newRecord(String memoryId, String tenantId, MemoryStatus status) {
        return newRecordWithHash(memoryId, tenantId, "hash_" + memoryId);
    }

    private MemoryRecord newRecordWithHash(String memoryId, String tenantId, String hash) {
        MemoryRecord rec = new MemoryRecord();
        rec.setMemoryId(memoryId);
        rec.setTenantId(tenantId);
        rec.setType(MemoryType.EPISODIC);
        rec.setStatus(MemoryStatus.ACTIVE);
        rec.setContent("content for " + memoryId);
        rec.setTopic("test-topic");
        rec.setImportanceScore(0.5);
        rec.setImportanceLevel("MEDIUM");
        rec.setContentHash(hash);
        return rec;
    }
}
