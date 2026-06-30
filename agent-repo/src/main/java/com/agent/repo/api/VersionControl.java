package com.agent.repo.api;

import com.agent.repo.model.AgentDefinition;
import com.agent.repo.model.AgentVersion;

import java.util.List;
import java.util.Optional;

/**
 * Agent version control + snapshot manager (doc 06-agent-repo §4.2, doc 01-database §6.2).
 *
 * <p>Each release generates a snapshot JSON containing the full AgentDefinition + changeLog.
 * Skeleton stage: in-memory ConcurrentHashMap. JPA-backed repository deferred to Plan 08 T3.</p>
 */
public interface VersionControl {

    /**
     * Create a version snapshot of the given agent.
     *
     * @param agent     agent to snapshot
     * @param changeLog change log entry
     * @return created AgentVersion
     */
    AgentVersion snapshot(AgentDefinition agent, String changeLog);

    /**
     * List all versions for an agent, ordered by version descending (latest first).
     */
    List<AgentVersion> listVersions(String agentId);

    /**
     * Get a specific version of an agent.
     */
    Optional<AgentVersion> getVersion(String agentId, int version);

    /**
     * Rollback an agent to a historical version. Returns a new AgentDefinition with the
     * snapshot's fields restored (caller is responsible for persisting via AgentRepository).
     *
     * @param agent   current agent definition (used as base for restoring version)
     * @param version target version to rollback to
     * @return AgentDefinition with restored fields, or empty if version not found
     */
    Optional<AgentDefinition> rollback(AgentDefinition agent, int version);

    /**
     * Count versions for an agent.
     */
    int countVersions(String agentId);
}
