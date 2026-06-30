package com.agent.repo.api.impl;

import com.agent.repo.api.AgentRepository;
import com.agent.repo.model.AgentDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory agent repository (doc 06-agent-repo §4.1).
 *
 * <p>Skeleton stage: ConcurrentHashMap keyed by agentId. JPA-backed repository deferred to
 * Plan 08 T2 deepening.</p>
 */
@Component
public class AgentRepositoryImpl implements AgentRepository {

    private final Map<String, AgentDefinition> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    @Override
    public AgentDefinition save(AgentDefinition agent) {
        if (agent == null || agent.getAgentId() == null || agent.getAgentId().isEmpty()) {
            throw new IllegalArgumentException("agent or agentId must not be null/empty");
        }
        long now = System.currentTimeMillis();
        // Preserve createdAt from existing record if present (re-save semantics)
        AgentDefinition existing = store.get(agent.getAgentId());
        if (existing != null && existing.getCreatedAt() != 0) {
            agent.setCreatedAt(existing.getCreatedAt());
        } else if (agent.getCreatedAt() == 0) {
            agent.setCreatedAt(now);
        }
        agent.setUpdatedAt(now);
        store.put(agent.getAgentId(), agent);
        return agent;
    }

    @Override
    public Optional<AgentDefinition> findById(String agentId) {
        if (agentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(agentId));
    }

    @Override
    public Optional<AgentDefinition> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return store.values().stream()
                .filter(a -> name.equals(a.getName()))
                .findFirst();
    }

    @Override
    public boolean existsByAgentId(String agentId) {
        return agentId != null && store.containsKey(agentId);
    }

    @Override
    public boolean existsByName(String name) {
        if (name == null) {
            return false;
        }
        return store.values().stream().anyMatch(a -> name.equals(a.getName()));
    }

    @Override
    public boolean deleteById(String agentId) {
        if (agentId == null) {
            return false;
        }
        return store.remove(agentId) != null;
    }

    @Override
    public List<AgentDefinition> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public long count() {
        return store.size();
    }

    /** Test-only helper to peek at the next id sequence value. */
    long peekNextId() {
        return idSeq.get() + 1;
    }
}
