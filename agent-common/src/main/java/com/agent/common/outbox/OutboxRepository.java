package com.agent.common.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Outbox message JPA repository (S-04 Plan Phase 1).
 *
 * <p>Provides status-based queries for {@link OutboxRelay} to poll
 * and update outbox messages.</p>
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * Find PENDING messages whose nextRetryAt is due, ordered by nextRetryAt (FIFO).
     * Uses Spring Data Pageable for limiting results.
     */
    @Query("SELECT m FROM OutboxMessage m WHERE m.status = :status " +
            "AND m.nextRetryAt <= :now ORDER BY m.nextRetryAt ASC")
    List<OutboxMessage> findByStatusAndNextRetryAtBefore(@Param("status") OutboxStatus status,
                                                          @Param("now") Instant now,
                                                          Pageable pageable);

    /**
     * Default findPending: PENDING messages whose nextRetryAt is due now, limited.
     */
    default List<OutboxMessage> findPending(int limit) {
        return findByStatusAndNextRetryAtBefore(OutboxStatus.PENDING, Instant.now(),
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /**
     * Mark a message as SENT and set sentAt timestamp.
     */
    @Modifying
    @Query("UPDATE OutboxMessage m SET m.status = 'SENT', m.sentAt = :sentAt " +
            "WHERE m.id = :id AND m.status = 'PENDING'")
    void markSent(@Param("id") Long id, @Param("sentAt") Instant sentAt);

    /**
     * Convenience: mark as SENT with current timestamp.
     */
    default void markSent(Long id) {
        markSent(id, Instant.now());
    }

    /**
     * Mark a message as FAILED, increment retryCount, and set nextRetryAt.
     */
    @Modifying
    @Query("UPDATE OutboxMessage m SET m.status = 'FAILED', " +
            "m.retryCount = m.retryCount + 1, m.nextRetryAt = :nextRetryAt " +
            "WHERE m.id = :id")
    void markFailed(@Param("id") Long id, @Param("nextRetryAt") Instant nextRetryAt);

    /**
     * Mark a message as DEAD (exceeded max retries).
     */
    @Modifying
    @Query("UPDATE OutboxMessage m SET m.status = 'DEAD' WHERE m.id = :id")
    void markDead(@Param("id") Long id);

    /**
     * Count messages by status.
     */
    long countByStatus(OutboxStatus status);

    /**
     * Find all messages by status (for monitoring / manual replay).
     */
    List<OutboxMessage> findByStatus(OutboxStatus status);

    /**
     * Convenience: find all DEAD messages.
     */
    default List<OutboxMessage> findDead() {
        return findByStatus(OutboxStatus.DEAD);
    }
}
