package com.agent.riskcontrol.entity;

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
 * JPA entity for content violation log entries.
 */
@Getter
@Setter
@Entity
@Table(name = "content_violation_log", uniqueConstraints = {
        @UniqueConstraint(name = "uk_violation_id", columnNames = "violation_id")
})
public class ContentViolationEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "violation_id", nullable = false, length = 64, unique = true)
    private String violationId;

    @Column(name = "content_type", length = 32)
    private String contentType;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "content", length = 512)
    private String content;

    @Column(name = "detail", length = 512)
    private String detail;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    /** JPA no-arg constructor. */
    public ContentViolationEntity() {
    }

    @PrePersist
    void onCreate() {
        if (detectedAt == null) {
            detectedAt = Instant.now();
        }
    }
}
