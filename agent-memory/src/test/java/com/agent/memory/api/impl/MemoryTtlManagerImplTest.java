package com.agent.memory.api.impl;

import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.MemoryRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryTtlManagerImplTest {

    @Test
    @DisplayName("isExpired 对 EPISODIC 记忆超过 30 天应返回 true")
    void should_ReturnExpired_When_EpisodicOlderThan30Days() {
        MemoryTtlManagerImpl manager = new MemoryTtlManagerImpl();
        MemoryRecord record = new MemoryRecord("mem_001", MemoryType.EPISODIC, "内容");
        record.setCreatedAt(Instant.now().minus(31, ChronoUnit.DAYS));

        assertThat(manager.isExpired(record)).isTrue();
    }

    @Test
    @DisplayName("isExpired 对 SEMANTIC 记忆未满 180 天应返回 false")
    void should_ReturnNotExpired_When_SemanticYoungerThan180Days() {
        MemoryTtlManagerImpl manager = new MemoryTtlManagerImpl();
        MemoryRecord record = new MemoryRecord("mem_002", MemoryType.SEMANTIC, "事实");
        record.setCreatedAt(Instant.now().minus(100, ChronoUnit.DAYS));

        assertThat(manager.isExpired(record)).isFalse();
    }

    @Test
    @DisplayName("archive 应将记忆状态置为 COLD")
    void should_SetStatusCold_When_ArchiveInvoked() {
        MemoryTtlManagerImpl manager = new MemoryTtlManagerImpl();
        MemoryRecord record = new MemoryRecord("mem_003", MemoryType.PROCEDURAL, "模板");

        manager.archive(record);

        assertThat(record.getStatus()).isEqualTo(MemoryStatus.COLD);
    }

    @Test
    @DisplayName("isExpired 对 null 记录应返回 false")
    void should_ReturnFalse_When_RecordIsNull() {
        MemoryTtlManagerImpl manager = new MemoryTtlManagerImpl();
        assertThat(manager.isExpired(null)).isFalse();
    }
}