package com.agent.memory.api;

import com.agent.memory.model.MemoryRecord;

/**
 * Memory TTL manager port (F12.D6: TTL expiry → archive + Plan 03 T8 state machine).
 *
 * <p>State transitions: RAW → ACTIVE → DISTILLED → ARCHIVED.
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

    /**
     * Apply TTL state machine to a single record (Plan 03 T8):
     * <ul>
     *   <li>RAW → ACTIVE：立即流转，设置 ttlExpireAt = now + activeToDistilled</li>
     *   <li>ACTIVE + expired → DISTILLED：重设 ttlExpireAt = now + distilledToArchived</li>
     *   <li>DISTILLED + expired → ARCHIVED</li>
     * </ul>
     *
     * @return true if a state transition occurred
     */
    boolean applyTtl(MemoryRecord record);

    /**
     * Batch cleanup expired memories for a tenant.
     *
     * @return number of records processed (transitioned or archived)
     */
    int cleanupExpired(String tenantId);
}
