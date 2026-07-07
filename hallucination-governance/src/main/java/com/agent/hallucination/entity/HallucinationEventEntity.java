package com.agent.hallucination.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * JPA Entity for hallucination event persistence (F10 L6 detected hallucination events).
 *
 * <p>Stores individual hallucination detection events with layer, result, and confidence.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "hallucination_event", uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_id", columnNames = "event_id")
}, indexes = {
        @Index(name = "idx_event_task_id", columnList = "task_id"),
        @Index(name = "idx_event_agent_id", columnList = "agent_id")
})
public class HallucinationEventEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64, unique = true)
    private String eventId;

    @Column(name = "task_id", length = 64)
    private String taskId;

    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "layer", length = 32)
    private String layer;

    @Column(name = "result", length = 32)
    private String result;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA no-arg constructor. */
    public HallucinationEventEntity() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
