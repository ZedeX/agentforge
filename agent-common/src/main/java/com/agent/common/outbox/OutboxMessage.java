package com.agent.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Outbox message entity for cross-service write compensation (S-04 Plan Phase 1).
 *
 * <p>Implements the local-message-table (Outbox) pattern:
 * business service writes this entity atomically with business data in the same
 * local transaction; {@link OutboxRelay} polls PENDING messages and publishes
 * them to RocketMQ for eventual consistency.</p>
 *
 * <p>Table: {@code outbox_message} in each service's own DB.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "outbox_message", indexes = {
        @Index(name = "idx_outbox_status_next_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_id"),
        @Index(name = "idx_outbox_created", columnList = "created_at")
})
public class OutboxMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JPA primary key (auto-increment). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Business aggregate ID (e.g., tool call ID, agent instance ID). */
    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    /** RocketMQ topic to publish to (e.g., "tool.audit", "runtime.stepstate"). */
    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    /** Message payload (JSON string). */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Outbox status (PENDING / SENT / FAILED / DEAD). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status = OutboxStatus.PENDING;

    /** Number of publish attempts so far. */
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /** Next retry timestamp (for exponential backoff). */
    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt;

    /** Creation timestamp. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Timestamp when message was successfully published. */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** JPA no-arg constructor. */
    public OutboxMessage() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (nextRetryAt == null) {
            nextRetryAt = Instant.now();
        }
    }
}
