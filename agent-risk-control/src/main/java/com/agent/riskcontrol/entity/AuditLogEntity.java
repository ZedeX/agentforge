package com.agent.riskcontrol.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * JPA entity for audit log entries.
 */
@Getter
@Setter
@Entity
@Table(name = "audit_log", uniqueConstraints = {
        @UniqueConstraint(name = "uk_audit_id", columnNames = "audit_id")
})
public class AuditLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "audit_id", nullable = false, length = 64, unique = true)
    private String auditId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "resource_type", length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    @Column(name = "result", length = 32)
    private String result;

    @Lob
    @Column(name = "detail")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA no-arg constructor. */
    public AuditLogEntity() {
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
