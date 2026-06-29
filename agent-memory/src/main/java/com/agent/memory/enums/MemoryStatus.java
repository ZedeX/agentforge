package com.agent.memory.enums;

/**
 * Memory temperature status (F12.D6 TTL expiry archive).
 */
public enum MemoryStatus {

    /** Recently accessed, kept in hot tier. */
    HOT,
    /** Infrequently accessed. */
    WARM,
    /** TTL reached, archived to cold storage. */
    COLD
}
