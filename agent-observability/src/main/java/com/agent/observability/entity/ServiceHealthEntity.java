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
 * JPA entity for service health records.
 */
@Getter
@Setter
@Entity
@Table(name = "service_health", uniqueConstraints = {
        @UniqueConstraint(name = "uk_service_name", columnNames = "service_name")
})
public class ServiceHealthEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "service_name", nullable = false, length = 64, unique = true)
    private String serviceName;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "error_rate")
    private Double errorRate;

    @Column(name = "latency_p95_ms")
    private Double latencyP95Ms;

    @Column(name = "detail", length = 512)
    private String detail;

    @Column(name = "last_checked")
    private Instant lastChecked;

    /** JPA no-arg constructor. */
    public ServiceHealthEntity() {
    }

    @PrePersist
    void onCreate() {
        if (lastChecked == null) {
            lastChecked = Instant.now();
        }
    }
}
