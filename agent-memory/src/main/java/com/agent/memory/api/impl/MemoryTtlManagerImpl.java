package com.agent.memory.api.impl;

import com.agent.memory.api.MemoryTtlManager;
import com.agent.memory.config.MemoryProperties;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.repository.MemoryRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 记忆 TTL 管理实现（F12.D6 + Plan 03 T8: TTL state machine + scheduled purge）。
 *
 * <p>状态流转：RAW → ACTIVE → DISTILLED → ARCHIVED
 * <ul>
 *   <li>RAW → ACTIVE：立即流转，ttlExpireAt = now + activeToDistilled</li>
 *   <li>ACTIVE + expired → DISTILLED：ttlExpireAt = now + distilledToArchived</li>
 *   <li>DISTILLED + expired → ARCHIVED</li>
 * </ul>
 *
 * <p>TTL 天数按 {@link MemoryType} 分配（fallback，当 ttlExpireAt 为 null 时使用）：
 * <ul>
 *   <li>EPISODIC：30 天 / SEMANTIC：180 天 / PROCEDURAL：365 天 / REFLECTIVE：90 天</li>
 * </ul>
 *
 * @see MemoryTtlManager
 * @see MemoryProperties.Ttl
 */
@Component
public class MemoryTtlManagerImpl implements MemoryTtlManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryTtlManagerImpl.class);

    /** EPISODIC 记忆 TTL（天）。 */
    public static final int TTL_EPISODIC_DAYS = 30;
    /** SEMANTIC 记忆 TTL（天）。 */
    public static final int TTL_SEMANTIC_DAYS = 180;
    /** PROCEDURAL 记忆 TTL（天）。 */
    public static final int TTL_PROCEDURAL_DAYS = 365;
    /** REFLECTIVE 记忆 TTL（天）。 */
    public static final int TTL_REFLECTIVE_DAYS = 90;

    /** 批量清理分页大小。 */
    private static final int CLEANUP_BATCH_SIZE = 100;

    private final MemoryRecordRepository repository;
    private final MemoryProperties properties;

    public MemoryTtlManagerImpl(MemoryRecordRepository repository, MemoryProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    // ============ isExpired ============

    @Override
    public boolean isExpired(MemoryRecord record) {
        if (record == null) {
            return false;
        }
        // 优先使用 ttlExpireAt 字段（T8 精确过期判断）
        if (record.getTtlExpireAt() != null) {
            boolean expired = record.getTtlExpireAt().isBefore(Instant.now());
            if (expired) {
                log.debug("记忆已过期（ttlExpireAt）memoryId={} expireAt={}",
                        record.getMemoryId(), record.getTtlExpireAt());
            }
            return expired;
        }
        // Fallback：createdAt + typeTtl（兼容未设 ttlExpireAt 的旧记录）
        Instant createdAt = record.getCreatedAt();
        if (createdAt == null) {
            return false;
        }
        int ttl = ttlForType(record.getType());
        long ageDays = ChronoUnit.DAYS.between(createdAt, Instant.now());
        boolean expired = ageDays > ttl;
        if (expired) {
            log.debug("记忆已过期（typeTtl）memoryId={} ageDays={} ttl={}",
                    record.getMemoryId(), ageDays, ttl);
        }
        return expired;
    }

    // ============ archive ============

    @Override
    public void archive(MemoryRecord record) {
        if (record == null) {
            log.warn("归档失败：MemoryRecord 为 null");
            return;
        }
        record.setStatus(MemoryStatus.ARCHIVED);
        log.info("记忆已归档至归档存储 memoryId={}", record.getMemoryId());
    }

    // ============ applyTtl 状态机 ============

    @Override
    public boolean applyTtl(MemoryRecord record) {
        if (record == null || record.getStatus() == null) {
            return false;
        }

        switch (record.getStatus()) {
            case RAW: {
                // RAW → ACTIVE：立即流转
                record.setStatus(MemoryStatus.ACTIVE);
                Duration activeTtl = parseDuration(properties.getTtl().getActiveToDistilled());
                record.setTtlExpireAt(Instant.now().plus(activeTtl));
                log.info("TTL 流转 RAW→ACTIVE memoryId={} nextExpireAt={}",
                        record.getMemoryId(), record.getTtlExpireAt());
                return true;
            }
            case ACTIVE: {
                if (isExpired(record)) {
                    // ACTIVE → DISTILLED（T4 MemoryDistiller 未集成，直接流转）
                    record.setStatus(MemoryStatus.DISTILLED);
                    Duration distilledTtl = parseDuration(properties.getTtl().getDistilledToArchived());
                    record.setTtlExpireAt(Instant.now().plus(distilledTtl));
                    log.info("TTL 流转 ACTIVE→DISTILLED memoryId={} nextExpireAt={}",
                            record.getMemoryId(), record.getTtlExpireAt());
                    return true;
                }
                return false;
            }
            case DISTILLED: {
                if (isExpired(record)) {
                    // DISTILLED → ARCHIVED
                    archive(record);
                    log.info("TTL 流转 DISTILLED→ARCHIVED memoryId={}", record.getMemoryId());
                    return true;
                }
                return false;
            }
            case ARCHIVED: {
                // 已归档，无进一步流转（物理删除留待后续实现）
                return false;
            }
            default:
                return false;
        }
    }

    // ============ cleanupExpired 批量清理 ============

    @Override
    public int cleanupExpired(String tenantId) {
        if (tenantId == null) {
            log.warn("批量清理失败：tenantId 为 null");
            return 0;
        }

        Instant now = Instant.now();
        int processed = 0;
        int page = 0;
        List<MemoryStatus> activeStates = List.of(MemoryStatus.ACTIVE, MemoryStatus.DISTILLED);

        while (true) {
            Page<MemoryRecord> batch = repository.findByTenantIdAndStatusInAndTtlExpireAtBefore(
                    tenantId, activeStates, now, PageRequest.of(page, CLEANUP_BATCH_SIZE));

            for (MemoryRecord record : batch.getContent()) {
                boolean transitioned = applyTtl(record);
                if (transitioned) {
                    repository.save(record);
                    processed++;
                }
            }

            if (!batch.hasNext()) {
                break;
            }
            page++;
        }

        log.info("批量清理完成 tenantId={} processed={}", tenantId, processed);
        return processed;
    }

    // ============ 辅助方法 ============

    /**
     * 按 MemoryType 返回默认 TTL 天数；null 视为 EPISODIC。
     */
    private int ttlForType(MemoryType type) {
        if (type == null) {
            return TTL_EPISODIC_DAYS;
        }
        switch (type) {
            case EPISODIC: return TTL_EPISODIC_DAYS;
            case SEMANTIC: return TTL_SEMANTIC_DAYS;
            case PROCEDURAL: return TTL_PROCEDURAL_DAYS;
            case REFLECTIVE: return TTL_REFLECTIVE_DAYS;
            default: return TTL_EPISODIC_DAYS;
        }
    }

    /**
     * 解析时长字符串：支持 "0"（立即）、"7d"（天）、"1h"（小时）、"30m"（分钟）、"60s"（秒）。
     */
    static Duration parseDuration(String s) {
        if (s == null || s.trim().isEmpty() || "0".equals(s.trim())) {
            return Duration.ZERO;
        }
        String trimmed = s.trim();
        try {
            if (trimmed.endsWith("d")) {
                return Duration.ofDays(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
            }
            if (trimmed.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
            }
            if (trimmed.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
            }
            if (trimmed.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
            }
            return Duration.ofSeconds(Long.parseLong(trimmed));
        } catch (NumberFormatException e) {
            log.warn("无法解析时长字符串：{}，默认 0", s);
            return Duration.ZERO;
        }
    }
}
