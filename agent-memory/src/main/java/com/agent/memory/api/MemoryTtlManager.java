package com.agent.memory.api;

import com.agent.memory.model.MemoryRecord;

/**
 * Memory TTL manager port (F12.D6: TTL expiry -> archive to cold storage).
 */
public interface MemoryTtlManager {

    /**
     * Check whether a memory record has exceeded its TTL.
     */
    boolean isExpired(MemoryRecord record);

    /**
     * Archive an expired memory record to cold storage.
     */
    void archive(MemoryRecord record);
}
