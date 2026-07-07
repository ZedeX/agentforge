package com.agent.observability.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * JPA entity for trace records.
 */
@Getter
@Setter
@Entity
@Table(name = "trace", uniqueConstraints = {
        @UniqueConstraint(name = "uk_trace_id", columnNames = "trace_id")
})
public class TraceEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 64, unique = true)
    private String traceId;

    @Column(name = "root_service", length = 64)
    private String rootService;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "span_count")
    private Integer spanCount;

    @Column(name = "status", length = 16)
    private String status;

    /** JPA no-arg constructor. */
    public TraceEntity() {
    }

    @PrePersist
    void onCreate() {
        if (startTime == null) {
            startTime = Instant.now();
        }
    }
}
