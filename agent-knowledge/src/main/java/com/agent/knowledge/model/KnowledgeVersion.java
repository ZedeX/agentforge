package com.agent.knowledge.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Knowledge base version snapshot (doc 06-agent-repo §4.2 version pattern applied to KB,
 * Plan 08 T8).
 *
 * <p>JPA Entity backing knowledge_version table. Each publish / config change of
 * KnowledgeBase generates an immutable snapshot row with the full KB metadata JSON.
 * Wave 24 refactor: removed {@code final} modifiers to satisfy JPA no-arg + setter
 * requirements; added protected no-arg constructor and {@link PrePersist} hook.</p>
 */
@Entity
@Table(name = "knowledge_version", uniqueConstraints = @UniqueConstraint(name = "uk_version_id", columnNames = "version_id"))
public class KnowledgeVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false, length = 32, unique = true)
    private String versionId;

    @Column(name = "kb_id", nullable = false, length = 32)
    private String kbId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "snapshot", nullable = false, length = 65535)
    private String snapshot;

    @Column(name = "change_log", length = 65535)
    private String changeLog;

    @Column(name = "created_at", nullable = false, updatable = false)
    private long createdAt;

    public KnowledgeVersion() {
    }

    public KnowledgeVersion(String versionId, String kbId, int version,
                            String snapshot, String changeLog, long createdAt) {
        this.versionId = versionId;
        this.kbId = kbId;
        this.version = version;
        this.snapshot = snapshot;
        this.changeLog = changeLog;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == 0) {
            createdAt = System.currentTimeMillis();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVersionId() { return versionId; }
    public void setVersionId(String versionId) { this.versionId = versionId; }

    public String getKbId() { return kbId; }
    public void setKbId(String kbId) { this.kbId = kbId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }

    public String getChangeLog() { return changeLog; }
    public void setChangeLog(String changeLog) { this.changeLog = changeLog; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
