package com.agent.memory.ttl;

import com.agent.memory.api.MemoryTtlManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TTL 定时调度器（Plan 03 T8）。
 *
 * <p>按固定延迟扫描所有租户的过期记忆，触发 TTL 状态流转。
 * 扫描间隔通过 {@code memory.ttl.scanIntervalMs} 配置（默认 1 小时）。
 *
 * <p>注意：当前实现扫描固定租户 "default"。多租户遍历需后续扩展。
 */
@Component
public class MemoryTtlScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryTtlScheduler.class);

    private final MemoryTtlManager ttlManager;

    public MemoryTtlScheduler(MemoryTtlManager ttlManager) {
        this.ttlManager = ttlManager;
    }

    /**
     * 定时扫描过期记忆（默认 1 小时间隔）。
     */
    @Scheduled(fixedDelayString = "${memory.ttl.scanIntervalMs:3600000}")
    public void scheduledScan() {
        log.info("TTL 定时扫描启动");
        try {
            int processed = ttlManager.cleanupExpired("default");
            log.info("TTL 定时扫描完成 processed={}", processed);
        } catch (Exception e) {
            // Intentionally swallowed: @Scheduled methods must not throw, or Spring
            // will stop the scheduler. The next scheduled run will retry cleanup.
            // ADR-006 compliant: documented reason for swallow.
            log.error("TTL 定时扫描异常", e);
        }
    }
}
