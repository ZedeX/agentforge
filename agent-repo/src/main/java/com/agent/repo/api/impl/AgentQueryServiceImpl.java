package com.agent.repo.api.impl;

import com.agent.repo.api.AgentQueryService;
import com.agent.repo.model.AgentDefinition;
import com.agent.repo.model.PageResult;
import com.agent.repo.model.RepoQuery;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * In-memory agent query service (doc 06-agent-repo §5).
 *
 * <p>Skeleton stage: maintains a ConcurrentHashMap index of agentId → AgentDefinition.
 * Filtering: by status (exact) / nameContains (case-insensitive substring) / agentTier (exact) /
 * capabilityTag (any of agent's abilityTags matches the tag's description or code).
 * Pagination: page (0-based) / size (default 10).</p>
 */
@Component
public class AgentQueryServiceImpl implements AgentQueryService {

    private final Map<String, AgentDefinition> index = new ConcurrentHashMap<>();

    @Override
    public void index(AgentDefinition agent) {
        if (agent == null || agent.getAgentId() == null) {
            return;
        }
        index.put(agent.getAgentId(), agent);
    }

    @Override
    public boolean removeIndex(String agentId) {
        if (agentId == null) {
            return false;
        }
        return index.remove(agentId) != null;
    }

    @Override
    public PageResult<AgentDefinition> query(RepoQuery query) {
        if (query == null) {
            return new PageResult<>(new ArrayList<>(), 0, 0, 0);
        }
        Stream<AgentDefinition> stream = index.values().stream();
        // Filter by status
        if (query.getStatus() != null) {
            stream = stream.filter(a -> query.getStatus().equals(a.getStatus()));
        }
        // Filter by nameContains (case-insensitive substring)
        if (query.getNameContains() != null && !query.getNameContains().isEmpty()) {
            String needle = query.getNameContains().toLowerCase();
            stream = stream.filter(a -> a.getName() != null && a.getName().toLowerCase().contains(needle));
        }
        // Filter by agentTier
        if (query.getAgentTier() != null) {
            stream = stream.filter(a -> query.getAgentTier().equals(a.getAgentTier()));
        }
        // Filter by capabilityTag — match if any abilityTag equals tag code or description
        if (query.getCapabilityTag() != null) {
            String tagCode = query.getCapabilityTag().getCode();
            stream = stream.filter(a -> a.getAbilityTags() != null && a.getAbilityTags().contains(tagCode));
        }
        // Collect + sort by name for stable order
        List<AgentDefinition> all = new ArrayList<>();
        stream.sorted(Comparator.comparing(AgentDefinition::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(all::add);
        long total = all.size();
        // Pagination
        int page = Math.max(0, query.getPage());
        int size = query.getSize() > 0 ? query.getSize() : 10;
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<AgentDefinition> pageItems = new ArrayList<>(all.subList(from, to));
        return new PageResult<>(pageItems, total, page, size);
    }
}
