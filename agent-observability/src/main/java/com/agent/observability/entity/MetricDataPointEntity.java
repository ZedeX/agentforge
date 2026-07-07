package com.agent.observability.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * JPA entity for metric data points.
 */
@Getter
@Setter
@Entity
@Table(name = "metric_data_point")
public class MetricDataPointEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "metric_name", nullable = false, length = 64)
    private String metricName;

    @Column(name = "service_name", nullable = false, length = 64)
    private String serviceName;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "metric_value", nullable = false)
    private Double value;

    @Lob
    @Column(name = "labels_json")
    private String labelsJson;

    /** JPA no-arg constructor. */
    public MetricDataPointEntity() {
    }

    @PrePersist
    void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
