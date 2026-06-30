package com.agent.repo.api;

import com.agent.repo.model.AgentDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Agent definition repository (doc 06-agent-repo §4.1 CRUD, doc 01-database §6.1 agent_definition).
 *
 * <p>Skeleton stage: in-memory ConcurrentHashMap. JPA-backed repository deferred to Plan 08 T2.</p>
 */
public interface AgentRepository {

    /**
     * Save (create or update) an agent definition.
     *
     * @param agent agent to save
     * @return saved agent (with assigned id / timestamps)
     */
    AgentDefinition save(AgentDefinition agent);

    /**
     * Find agent by agentId.
     */
    Optional<AgentDefinition> findById(String agentId);

    /**
     * Find agent by name (case-sensitive exact match).
     */
    Optional<AgentDefinition> findByName(String name);

    /**
     * Check existence by agentId.
     */
    boolean existsByAgentId(String agentId);

    /**
     * Check existence by name (used for uniqueness validation on create).
     */
    boolean existsByName(String name);

    /**
     * Delete agent by agentId.
     *
     * @return true if deleted, false if not found
     */
    boolean deleteById(String agentId);

    /**
     * List all agents.
     */
    List<AgentDefinition> findAll();

    /**
     * Count total agents.
     */
    long count();
}
