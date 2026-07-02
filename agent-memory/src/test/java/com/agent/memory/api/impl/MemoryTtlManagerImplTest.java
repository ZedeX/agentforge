package com.agent.memory.api.impl;

import com.agent.memory.config.MemoryProperties;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.repository.MemoryRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryTtlManagerImplTest {

    private static final String TENANT = "tenant_001";

    private MemoryTtlManagerImpl createManager(MemoryRecordRepository repository) {
        return new MemoryTtlManagerImpl(repository, new MemoryProperties());
    }

    private MemoryTtlManagerImpl createManager(MemoryRecordRepository repository, MemoryProperties props) {
        return new MemoryTtlManagerImpl(repository, props);
    }

    // ============ isExpired ============

    @Test
    @DisplayName("isExpired 对 EPISODIC 记忆超过 30 天应返回 true")
    void should_ReturnExpired_When_EpisodicOlderThan30Days() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        MemoryRecord record = new MemoryRecord("mem_001", MemoryType.EPISODIC, "内容");
        record.setCreatedAt(Instant.now().minus(31, ChronoUnit.DAYS));

        assertThat(manager.isExpired(record)).isTrue();
    }

    @Test
    @DisplayName("isExpired 对 SEMANTIC 记忆未满 180 天应返回 false")
    void should_ReturnNotExpired_When_SemanticYoungerThan180Days() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        MemoryRecord record = new MemoryRecord("mem_002", MemoryType.SEMANTIC, "事实");
        record.setCreatedAt(Instant.now().minus(100, ChronoUnit.DAYS));

        assertThat(manager.isExpired(record)).isFalse();
    }

    @Test
    @DisplayName("isExpired 对 null 记录应返回 false")
    void should_ReturnFalse_When_RecordIsNull() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        assertThat(manager.isExpired(null)).isFalse();
    }

    @Test
    @DisplayName("isExpired 优先使用 ttlExpireAt 字段判断（过去时间 → true）")
    void should_UseTtlExpireAt_When_FieldSet() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        MemoryRecord record = new MemoryRecord("mem_003", MemoryType.EPISODIC, "内容");
        record.setTtlExpireAt(Instant.now().minus(1, ChronoUnit.HOURS));

        assertThat(manager.isExpired(record)).isTrue();
    }

    @Test
    @DisplayName("isExpired 使用 ttlExpireAt 字段判断（未来时间 → false）")
    void should_ReturnFalse_When_TtlExpireAtInFuture() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        MemoryRecord record = new MemoryRecord("mem_004", MemoryType.EPISODIC, "内容");
        record.setTtlExpireAt(Instant.now().plus(7, ChronoUnit.DAYS));

        assertThat(manager.isExpired(record)).isFalse();
    }

    // ============ archive ============

    @Test
    @DisplayName("archive 应将记忆状态置为 ARCHIVED")
    void should_SetStatusArchived_When_ArchiveInvoked() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        MemoryRecord record = new MemoryRecord("mem_005", MemoryType.PROCEDURAL, "模板");

        manager.archive(record);

        assertThat(record.getStatus()).isEqualTo(MemoryStatus.ARCHIVED);
    }

    // ============ applyTtl 状态机 ============

    @Test
    @DisplayName("applyTtl 对 RAW 应流转为 ACTIVE 并设置 ttlExpireAt")
    void should_TransitionRawToActive_When_ApplyTtlInvoked() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        MemoryRecord record = new MemoryRecord("mem_raw", MemoryType.EPISODIC, "内容");
        record.setStatus(MemoryStatus.RAW);
        record.setTenantId(TENANT);

        boolean transitioned = manager.applyTtl(record);

        assertThat(transitioned).isTrue();
        assertThat(record.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
        assertThat(record.getTtlExpireAt()).isNotNull();
        // 默认 activeToDistilled = "7d"
        assertThat(record.getTtlExpireAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("applyTtl 对未过期的 ACTIVE 应不流转")
    void should_NotTransition_When_ActiveNotExpired() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        MemoryRecord record = new MemoryRecord("mem_active", MemoryType.EPISODIC, "内容");
        record.setStatus(MemoryStatus.ACTIVE);
        record.setTtlExpireAt(Instant.now().plus(7, ChronoUnit.DAYS));

        boolean transitioned = manager.applyTtl(record);

        assertThat(transitioned).isFalse();
        assertThat(record.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
    }

    @Test
    @DisplayName("applyTtl 对过期的 ACTIVE 应流转为 DISTILLED 并重设 ttlExpireAt")
    void should_TransitionActiveToDistilled_When_Expired() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        MemoryRecord record = new MemoryRecord("mem_active_exp", MemoryType.EPISODIC, "内容");
        record.setStatus(MemoryStatus.ACTIVE);
        record.setTtlExpireAt(Instant.now().minus(1, ChronoUnit.DAYS));

        boolean transitioned = manager.applyTtl(record);

        assertThat(transitioned).isTrue();
        assertThat(record.getStatus()).isEqualTo(MemoryStatus.DISTILLED);
        // 默认 distilledToArchived = "30d"
        assertThat(record.getTtlExpireAt()).isAfter(Instant.now().plus(29, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("applyTtl 对过期的 DISTILLED 应流转为 ARCHIVED")
    void should_TransitionDistilledToArchived_When_Expired() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        MemoryRecord record = new MemoryRecord("mem_distilled", MemoryType.EPISODIC, "内容");
        record.setStatus(MemoryStatus.DISTILLED);
        record.setTtlExpireAt(Instant.now().minus(1, ChronoUnit.DAYS));

        boolean transitioned = manager.applyTtl(record);

        assertThat(transitioned).isTrue();
        assertThat(record.getStatus()).isEqualTo(MemoryStatus.ARCHIVED);
    }

    @Test
    @DisplayName("applyTtl 对 ARCHIVED 应不流转")
    void should_NotTransition_When_AlreadyArchived() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        MemoryRecord record = new MemoryRecord("mem_archived", MemoryType.EPISODIC, "内容");
        record.setStatus(MemoryStatus.ARCHIVED);

        boolean transitioned = manager.applyTtl(record);

        assertThat(transitioned).isFalse();
        assertThat(record.getStatus()).isEqualTo(MemoryStatus.ARCHIVED);
    }

    @Test
    @DisplayName("applyTtl 对 null 记录应返回 false")
    void should_ReturnFalse_When_ApplyTtlNullRecord() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        assertThat(manager.applyTtl(null)).isFalse();
    }

    // ============ cleanupExpired 批量清理 ============

    @Test
    @DisplayName("cleanupExpired 应分页查询过期记录并调用 applyTtl + save")
    void should_QueryAndProcess_When_CleanupExpired() {
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        MemoryTtlManagerImpl manager = createManager(repository);

        MemoryRecord expired1 = new MemoryRecord("mem_e1", MemoryType.EPISODIC, "内容1");
        expired1.setStatus(MemoryStatus.ACTIVE);
        expired1.setTtlExpireAt(Instant.now().minus(1, ChronoUnit.DAYS));

        MemoryRecord expired2 = new MemoryRecord("mem_e2", MemoryType.EPISODIC, "内容2");
        expired2.setStatus(MemoryStatus.DISTILLED);
        expired2.setTtlExpireAt(Instant.now().minus(1, ChronoUnit.DAYS));

        Page<MemoryRecord> page = new PageImpl<>(List.of(expired1, expired2));
        when(repository.findByTenantIdAndStatusInAndTtlExpireAtBefore(
                eq(TENANT), anyList(), any(Instant.class), any()))
                .thenReturn(page);

        int processed = manager.cleanupExpired(TENANT);

        assertThat(processed).isEqualTo(2);
        assertThat(expired1.getStatus()).isEqualTo(MemoryStatus.DISTILLED);
        assertThat(expired2.getStatus()).isEqualTo(MemoryStatus.ARCHIVED);
        verify(repository).save(expired1);
        verify(repository).save(expired2);
    }

    @Test
    @DisplayName("cleanupExpired 对空结果应返回 0")
    void should_ReturnZero_When_NoExpiredRecords() {
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        MemoryTtlManagerImpl manager = createManager(repository);

        Page<MemoryRecord> emptyPage = new PageImpl<>(List.of());
        when(repository.findByTenantIdAndStatusInAndTtlExpireAtBefore(
                anyString(), anyList(), any(Instant.class), any()))
                .thenReturn(emptyPage);

        int processed = manager.cleanupExpired(TENANT);

        assertThat(processed).isEqualTo(0);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("cleanupExpired 对 null tenantId 应返回 0")
    void should_ReturnZero_When_TenantIdIsNull() {
        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class));
        assertThat(manager.cleanupExpired(null)).isEqualTo(0);
    }

    // ============ parseDuration 工具方法 ============

    @Test
    @DisplayName("parseDuration 应正确解析天/小时/分钟/秒")
    void should_ParseDuration_When_ValidFormat() {
        assertThat(MemoryTtlManagerImpl.parseDuration("7d")).isEqualTo(Duration.ofDays(7));
        assertThat(MemoryTtlManagerImpl.parseDuration("1h")).isEqualTo(Duration.ofHours(1));
        assertThat(MemoryTtlManagerImpl.parseDuration("30m")).isEqualTo(Duration.ofMinutes(30));
        assertThat(MemoryTtlManagerImpl.parseDuration("60s")).isEqualTo(Duration.ofSeconds(60));
        assertThat(MemoryTtlManagerImpl.parseDuration("0")).isEqualTo(Duration.ZERO);
        assertThat(MemoryTtlManagerImpl.parseDuration("")).isEqualTo(Duration.ZERO);
        assertThat(MemoryTtlManagerImpl.parseDuration(null)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("parseDuration 对无效格式应返回 ZERO")
    void should_ReturnZero_When_InvalidFormat() {
        assertThat(MemoryTtlManagerImpl.parseDuration("abc")).isEqualTo(Duration.ZERO);
    }

    // ============ 自定义 TTL 配置 ============

    @Test
    @DisplayName("applyTtl 应使用自定义 TTL 配置设置 ttlExpireAt")
    void should_UseCustomTtl_When_PropertiesConfigured() {
        MemoryProperties props = new MemoryProperties();
        props.getTtl().setActiveToDistilled("1d"); // 1 day instead of default 7d

        MemoryTtlManagerImpl manager = createManager(mock(MemoryRecordRepository.class), props);
        MemoryRecord record = new MemoryRecord("mem_custom", MemoryType.EPISODIC, "内容");
        record.setStatus(MemoryStatus.RAW);

        manager.applyTtl(record);

        assertThat(record.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
        // ttlExpireAt should be ~1 day from now, not 7 days
        assertThat(record.getTtlExpireAt()).isBefore(Instant.now().plus(2, ChronoUnit.DAYS));
        assertThat(record.getTtlExpireAt()).isAfter(Instant.now());
    }
}
