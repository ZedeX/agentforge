package com.agent.repo.api.impl;

import com.agent.repo.api.AgentLifecycleManager;
import com.agent.repo.enums.AgentStatus;
import com.agent.repo.model.AgentDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory lifecycle manager (doc 06-agent-repo §2.3).
 *
 * <p>Tracks latest AgentStatus per agentId in a ConcurrentHashMap. Validates state machine:
 * DRAFT → PUBLISHED → DEPRECATED → ARCHIVED. Rejects backward / illegal transitions.</p>
 */
@Component
public class AgentLifecycleManagerImpl implements AgentLifecycleManager {

    private final Map<String, AgentStatus> statusMap = new ConcurrentHashMap<>();

    @Override
    public void register(AgentDefinition agent) {
        if (agent == null || agent.getAgentId() == null) {
            return;
        }
        // Only register if not already tracked (preserve latest status on re-register)
        statusMap.putIfAbsent(agent.getAgentId(), agent.getStatus() != null ? agent.getStatus() : AgentStatus.DRAFT);
    }

    @Override
    public AgentStatus getCurrentStatus(String agentId) {
        if (agentId == null) {
            return null;
        }
        return statusMap.get(agentId);
    }

    @Override
    public boolean canTransition(String agentId, AgentStatus target) {
        if (agentId == null) {
            return false;
        }
        AgentStatus current = statusMap.get(agentId);
        if (current == null) {
            // Unregistered agent: allow any non-null target as initial registration
            return target != null;
        }
        return current.canTransitionTo(target);
    }

    @Override
    public AgentStatus transition(String agentId, AgentStatus target) {
        if (agentId == null) {
            throw new IllegalStateException("agentId must not be null");
        }
        if (target == null) {
            throw new IllegalStateException("target status must not be null");
        }
        AgentStatus current = statusMap.get(agentId);
        if (current == null) {
            // Auto-register with target status (initial registration)
            statusMap.put(agentId, target);
            return target;
        }
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal transition: " + current + " → " + target + " for agentId=" + agentId);
        }
        statusMap.put(agentId, target);
        return target;
    }

    /** Test-only helper to clear all tracked statuses. */
    void clear() {
        statusMap.clear();
    }
}
