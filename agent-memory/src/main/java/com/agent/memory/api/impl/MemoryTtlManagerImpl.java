package com.agent.memory.api.impl;

import com.agent.memory.api.MemoryTtlManager;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.MemoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 记忆 TTL 管理实现（F12.D6: TTL expiry -> archive to cold storage）。
 *
 * <p>按 {@link MemoryType} 分配不同 TTL 天数：
 * <ul>
 *   <li>EPISODIC（情景记忆）：30 天 —— 易过期</li>
 *   <li>SEMANTIC（语义记忆）：180 天 —— 中长期保留</li>
 *   <li>PROCEDURAL（程序记忆）：365 天 —— 长期保留</li>
 * </ul>
 * isExpired 按 createdAt + typeTtl 判断超期；archive 将状态置为 COLD。</p>
 *
 * @see MemoryTtlManager
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

    @Override
    public boolean isExpired(MemoryRecord record) {
        if (record == null) {
            return false;
        }
        Instant createdAt = record.getCreatedAt();
        if (createdAt == null) {
            return false;
        }
        int ttl = ttlForType(record.getType());
        long ageDays = ChronoUnit.DAYS.between(createdAt, Instant.now());
        boolean expired = ageDays > ttl;
        if (expired) {
            log.info("记忆已过期 memoryId={} ageDays={} ttl={}",
                    record.getMemoryId(), ageDays, ttl);
        }
        return expired;
    }

    @Override
    public void archive(MemoryRecord record) {
        if (record == null) {
            log.warn("归档失败：MemoryRecord 为 null");
            return;
        }
        record.setStatus(MemoryStatus.COLD);
        log.info("记忆已归档至冷存 memoryId={}", record.getMemoryId());
    }

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
            default: return TTL_EPISODIC_DAYS;
        }
    }
}