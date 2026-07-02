package com.agent.memory.repository;

import com.agent.memory.model.MemoryExtractLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemoryExtractLogRepository tests (Plan 03 T2).
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("MemoryExtractLogRepository 持久化测试")
class MemoryExtractLogRepositoryTest {

    @Autowired
    private MemoryExtractLogRepository repository;

    @Test
    @DisplayName("should save and find by id")
    void should_SaveAndFindById_When_AllFieldsPopulated() {
        MemoryExtractLog log = new MemoryExtractLog("tk-001", 5, 1, 250L);
        MemoryExtractLog saved = repository.saveAndFlush(log);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTaskId()).isEqualTo("tk-001");
        assertThat(saved.getExtractCount()).isEqualTo(5);
        assertThat(saved.getFailedCount()).isEqualTo(1);
        assertThat(saved.getDurationMs()).isEqualTo(250L);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("should find by taskId")
    void should_FindByTaskId_When_TaskIdMatches() {
        repository.save(new MemoryExtractLog("tk-002", 3, 0, 100L));
        repository.save(new MemoryExtractLog("tk-002", 4, 1, 200L));
        repository.save(new MemoryExtractLog("tk-003", 2, 0, 50L));

        List<MemoryExtractLog> results = repository.findByTaskId("tk-002");
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(l -> assertThat(l.getTaskId()).isEqualTo("tk-002"));
    }

    @Test
    @DisplayName("should set createdAt on persist via @PrePersist")
    void should_SetCreatedAt_When_Persisted() {
        MemoryExtractLog log = new MemoryExtractLog("tk-ts", 1, 0, 10L);
        MemoryExtractLog saved = repository.saveAndFlush(log);

        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
