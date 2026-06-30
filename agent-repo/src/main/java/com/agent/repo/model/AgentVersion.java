package com.agent.repo.model;

/**
 * Agent version snapshot (doc 01-database §6.2 agent_version table, doc 06-agent-repo §4.2).
 *
 * <p>Each release generates a snapshot JSON containing the full AgentDefinition, plus a change_log.
 * Skeleton stage: in-memory POJO. JPA Entity deferred to Plan 08 T3.</p>
 */
public class AgentVersion {

    private Long id;
    private String agentId;
    private int version;
    /** Serialized AgentDefinition JSON snapshot. */
    private String snapshot;
    private String changeLog;
    private long createdAt;

    public AgentVersion() {
    }

    public AgentVersion(String agentId, int version, String snapshot, String changeLog) {
        this.agentId = agentId;
        this.version = version;
        this.snapshot = snapshot;
        this.changeLog = changeLog;
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
