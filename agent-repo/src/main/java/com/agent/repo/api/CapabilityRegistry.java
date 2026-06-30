package com.agent.repo.api;

import com.agent.repo.enums.CapabilityTag;
import com.agent.repo.model.Capability;

import java.util.List;
import java.util.Optional;

/**
 * Capability registry (doc 06-agent-repo §3.1).
 *
 * <p>Maintains a catalog of Capabilities tagged by CapabilityTag, used by Agents to advertise
 * what they can do and by AgentQueryService to filter Agents by capability.</p>
 */
public interface CapabilityRegistry {

    /**
     * Register (or update) a capability.
     */
    void register(Capability capability);

    /**
     * Find capability by code.
     */
    Optional<Capability> find(String code);

    /**
     * List all registered capabilities.
     */
    List<Capability> list();

    /**
     * List capabilities by tag.
     */
    List<Capability> findByTag(CapabilityTag tag);

    /**
     * Remove a capability by code.
     *
     * @return true if removed, false if not found
     */
    boolean remove(String code);

    /**
     * Check if capability is registered.
     */
    boolean exists(String code);
}
