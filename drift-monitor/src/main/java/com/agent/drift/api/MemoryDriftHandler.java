package com.agent.drift.api;

/**
 * Memory drift handler port (F11 memory drift: mark invalid + archive).
 */
public interface MemoryDriftHandler {

    /**
     * Mark a memory record as invalid (wrong recall).
     */
    void markInvalid(String memoryId);

    /**
     * Archive an expired/cold memory record.
     */
    void archive(String memoryId);
}
