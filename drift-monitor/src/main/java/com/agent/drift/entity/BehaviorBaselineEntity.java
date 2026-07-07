package com.agent.drift.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * JPA Entity for behavior baseline persistence (F11 L1 baseline anchor).
 *
 * <p>Stores agent behavior baselines anchored on first run.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "behavior_baseline", indexes = {
        @Index(name = "idx_baseline_agent_type", columnList = "agent_id,baseline_type")
})
public class BehaviorBaselineEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "baseline_type", nullable = false, length = 32)
    private String baselineType;

    @Column(name = "baseline_value")
    private Double baselineValue;

    @Column(name = "observation_count")
    private Integer observationCount;

    @Column(name = "last_updated")
    private Instant lastUpdated;

    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    /** JPA no-arg constructor. */
    public BehaviorBaselineEntity() {
    }

    @PrePersist
    void onCreate() {
        if (lastUpdated == null) {
            lastUpdated = Instant.now();
        }
        if (observationCount == null) {
            observationCount = 0;
        }
    }
}
