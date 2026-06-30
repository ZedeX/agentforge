package com.agent.knowledge.model;

/**
 * Knowledge base version snapshot (doc 06-agent-repo §4.2 version pattern applied to KB).
 *
 * <p>Each publish / config change generates a snapshot of the KnowledgeBase metadata.
 * Skeleton stage: in-memory POJO. JPA Entity deferred to Plan 08 T8.</p>
 */
public class KnowledgeVersion {

    private final String versionId;
    private final String kbId;
    private final int version;
    private final String snapshot;
    private final String changeLog;
    private final long createdAt;

    public KnowledgeVersion(String versionId, String kbId, int version,
                            String snapshot, String changeLog, long createdAt) {
        this.versionId = versionId;
        this.kbId = kbId;
        this.version = version;
        this.snapshot = snapshot;
        this.changeLog = changeLog;
        this.createdAt = createdAt;
    }

    public String getVersionId() { return versionId; }

    public String getKbId() { return kbId; }

    public int getVersion() { return version; }

    public String getSnapshot() { return snapshot; }

    public String getChangeLog() { return changeLog; }

    public long getCreatedAt() { return createdAt; }
}
