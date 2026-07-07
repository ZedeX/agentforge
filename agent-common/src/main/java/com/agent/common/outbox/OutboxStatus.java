package com.agent.common.outbox;

/**
 * Outbox message status enum (S-04 Plan Phase 1).
 *
 * <p>State machine transitions:
 * <pre>
 *   PENDING ──→ SENT   (publish succeeded)
 *   PENDING ──→ FAILED (publish failed)
 *   FAILED  ──→ PENDING (retry, reset nextRetryAt)
 *   FAILED  ──→ DEAD   (retry_count exceeded max)
 *   SENT    ──→ (terminal)
 *   DEAD    ──→ (terminal)
 * </pre>
 */
public enum OutboxStatus {

    PENDING,
    SENT,
    FAILED,
    DEAD;

    /**
     * Check if transition from this status to target is legal.
     */
    public boolean canTransitionTo(OutboxStatus target) {
        return switch (this) {
            case PENDING -> target == SENT || target == FAILED;
            case FAILED -> target == PENDING || target == DEAD;
            case SENT, DEAD -> false;
        };
    }
}
