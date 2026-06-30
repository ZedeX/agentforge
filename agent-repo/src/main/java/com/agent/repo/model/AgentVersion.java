package com.agent.repo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Agent version snapshot (doc 01-database §6.2 agent_version table, doc 06-agent-repo §4.2, Plan 08 T3).
 *
 * <p>JPA Entity backing agent_version table. Each release generates a snapshot JSON containing
 * the full AgentDefinition, plus a change_log.</p>
 */
@Entity
@Table(name = "agent_version", uniqueConstraints = @UniqueConstraint(name = "uk_agent_version", columnNames = {"agent_id", "version"}))
public class AgentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "version", nullable = false)
    private int version;

    /** Serialized AgentDefinition JSON snapshot. */
    @Column(name = "snapshot", nullable = false, length = 65535)
    private String snapshot;

    @Column(name = "change_log", nullable = false, length = 65535)
    private String changeLog;

    @Column(name = "created_at", nullable = false, updatable = false)
    private long createdAt;

    public AgentVersion() {
    }

    public AgentVersion(String agentId, int version, String snapshot, String changeLog) {
        this.agentId = agentId;
        this.version = version;
        this.snapshot = snapshot;
        this.changeLog = changeLog;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == 0) {
            createdAt = System.currentTimeMillis();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }

    public String getChangeLog() { return changeLog; }
    public void setChangeLog(String changeLog) { this.changeLog = changeLog; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
