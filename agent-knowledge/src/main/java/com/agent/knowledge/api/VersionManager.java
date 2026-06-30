package com.agent.knowledge.api;

import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.model.KnowledgeVersion;

import java.util.List;

/**
 * Version manager (doc 06-agent-repo §4.2 version snapshot pattern applied to KB).
 *
 * <p>Snapshots KB metadata on each publish / config change. Supports rollback to prior version.
 * Skeleton stage: in-memory. JPA + Flyway deferred to Plan 08 T8.</p>
 */
public interface VersionManager {

    /**
     * Create a version snapshot of the current KB state.
     *
     * @param kbId      knowledge base id
     * @param changeLog human-readable change description
     * @return created KnowledgeVersion, null if KB not found
     */
    KnowledgeVersion snapshot(String kbId, String changeLog);

    /**
     * List all versions for a KB, newest first.
     *
     * @return list of versions, empty if KB not found
     */
    List<KnowledgeVersion> listVersions(String kbId);

    /**
     * Get a specific version of a KB.
     *
     * @return KnowledgeVersion, null if not found
     */
    KnowledgeVersion getVersion(String kbId, int version);

    /**
     * Rollback KB to a specific version's state.
     *
     * @return the rolled-back KnowledgeVersion, null if KB or version not found
     */
    KnowledgeVersion rollback(String kbId, int version);

    /**
     * Bind a KnowledgeBase to this manager (skeleton: registers KB for snapshotting).
     *
     * @param base knowledge base to bind
     */
    void bindBase(KnowledgeBase base);
}
