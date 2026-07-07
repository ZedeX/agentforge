package com.agent.common.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Consume log entry for idempotent outbox consumption (S-04 Plan Phase 2).
 *
 * <p>Mirrors the {@code event_consume_log} pattern from S-03 fix.
 * Each consumed message records its eventId here; duplicate deliveries
 * are detected by checking existence before processing.</p>
 */
@Getter
@Setter
@AllArgsConstructor
public class ConsumeLog {

    /** Event ID (= outbox_message.id, used as idempotency key). */
    private String eventId;

    /** Timestamp when the event was first consumed. */
    private Instant consumedAt;
}
