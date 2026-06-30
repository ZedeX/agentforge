package com.agent.repo.api;

import com.agent.repo.enums.AgentStatus;
import com.agent.repo.model.AgentDefinition;

/**
 * Agent lifecycle state machine manager (doc 06-agent-repo §2.3, PRD §二(二) agent-repo status flow).
 *
 * <p>Single-direction transitions: DRAFT → PUBLISHED → DEPRECATED → ARCHIVED.
 * Skeleton stage: in-memory tracking. JPA-backed status guard deferred to Plan 08 T4.</p>
 */
public interface AgentLifecycleManager {

    /**
     * Track an agent for lifecycle management (idempotent).
     *
     * @param agent agent to register
     */
    void register(AgentDefinition agent);

    /**
     * Get the currently tracked status for an agent.
     *
     * @return tracked AgentStatus, or null if agent is not registered
     */
    AgentStatus getCurrentStatus(String agentId);

    /**
     * Check whether a transition from current to target status is allowed.
     *
     * @param agentId agent id
     * @param target  target status
     * @return true if allowed (or agent not registered), false if explicitly denied
     */
    boolean canTransition(String agentId, AgentStatus target);

    /**
     * Transition agent to target status.
     *
     * @param agentId agent id
     * @param target  target status
     * @return new AgentStatus after transition
     * @throws IllegalStateException if transition is illegal or agent is not registered
     */
    AgentStatus transition(String agentId, AgentStatus target);
}
