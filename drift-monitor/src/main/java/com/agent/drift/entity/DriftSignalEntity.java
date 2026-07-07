package com.agent.drift.entity;

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
 * JPA Entity for drift signal persistence (F11 L2 detected drift signals).
 *
 * <p>Stores individual drift detection signals with observed/expected values and deviation.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "drift_signal", uniqueConstraints = {
        @UniqueConstraint(name = "uk_signal_id", columnNames = "signal_id")
}, indexes = {
        @Index(name = "idx_signal_agent", columnList = "agent_id"),
        @Index(name = "idx_signal_detected", columnList = "detected_at")
})
public class DriftSignalEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "signal_id", nullable = false, length = 64, unique = true)
    private String signalId;

    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "drift_type", length = 32)
    private String driftType;

    @Column(name = "drift_level", length = 32)
    private String driftLevel;

    @Column(name = "indicator", length = 128)
    private String indicator;

    @Column(name = "observed_value")
    private Double observedValue;

    @Column(name = "expected_value")
    private Double expectedValue;

    @Column(name = "deviation")
    private Double deviation;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    /** JPA no-arg constructor. */
    public DriftSignalEntity() {
    }

    @PrePersist
    void onCreate() {
        if (detectedAt == null) {
            detectedAt = Instant.now();
        }
    }
}
