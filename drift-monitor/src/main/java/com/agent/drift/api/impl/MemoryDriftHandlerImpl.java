package com.agent.drift.api.impl;

import com.agent.drift.api.MemoryDriftHandler;
import com.agent.drift.model.MemoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆漂移处理器实现 (F11 memory drift: 标记失效 + 归档)。
 *
 * <p>简单实现策略：使用 ConcurrentHashMap 维护 memoryId -> MemoryRecord 快照。
 * markInvalid 将记录置为 invalid; archive 将记录置为已归档 (设置 archiveLocation 与 expiredAt)。
 * 若 memoryId 不存在, 则创建占位记录后再标记 / 归档。</p>
 */
@Component
public class MemoryDriftHandlerImpl implements MemoryDriftHandler {

    private static final Logger log = LoggerFactory.getLogger(MemoryDriftHandlerImpl.class);

    private static final String DEFAULT_ARCHIVE_LOCATION = "memory-archive://cold-storage";

    private final Map<String, MemoryRecord> store = new ConcurrentHashMap<>();

    @Override
    public void markInvalid(String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            log.warn("记忆漂移: markInvalid 收到空 memoryId, 跳过");
            return;
        }
        MemoryRecord record = store.computeIfAbsent(memoryId, id -> new MemoryRecord(id, 0.0));
        record.setInvalid(true);
        log.info("记忆漂移: 标记失效 memoryId={}", memoryId);
    }

    @Override
    public void archive(String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            log.warn("记忆漂移: archive 收到空 memoryId, 跳过");
            return;
        }
        MemoryRecord record = store.computeIfAbsent(memoryId, id -> new MemoryRecord(id, 0.0));
        record.setExpiredAt(Instant.now());
        record.setArchiveLocation(DEFAULT_ARCHIVE_LOCATION);
        log.info("记忆漂移: 归档 memoryId={}, archiveLocation={}", memoryId, record.getArchiveLocation());
    }

    public MemoryRecord getRecord(String memoryId) {
        return store.get(memoryId);
    }

    /** 供测试 / 外部预置记录使用。 */
    public void putRecord(MemoryRecord record) {
        if (record != null && record.getMemoryId() != null) {
            store.put(record.getMemoryId(), record);
        }
    }

    public int snapshotSize() {
        return store.size();
    }
}