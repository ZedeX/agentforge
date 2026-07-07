package com.agent.common.outbox;

/**
 * Repository interface for consume log (idempotency table) (S-04 Plan Phase 2).
 *
 * <p>Mirrors the {@code event_consume_log} pattern from S-03 fix.
 * Implementations can be JPA-backed (each service creates {@code consume_log} table
 * in its own DB) or Redis-backed.</p>
 */
public interface ConsumeLogRepository {

    /**
     * Check if an event has already been consumed.
     *
     * @param eventId the event ID (idempotency key)
     * @return true if already consumed
     */
    boolean existsById(String eventId);

    /**
     * Record that an event has been consumed.
     *
     * @param eventId the event ID
     * @return the saved consume log entry
     */
    ConsumeLog save(String eventId);
}
