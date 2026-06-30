package com.agent.repo.api;

import com.agent.repo.model.AgentDefinition;
import com.agent.repo.model.PageResult;
import com.agent.repo.model.RepoQuery;

/**
 * Agent query service (doc 06-agent-repo §5 ListAgents RPC).
 *
 * <p>Indexes agent definitions and supports filtering by status / name contains / tier /
 * capability tag, plus pagination. Skeleton stage: in-memory index.</p>
 */
public interface AgentQueryService {

    /**
     * Index an agent for querying (idempotent — re-indexing updates the existing entry).
     *
     * @param agent agent to index
     */
    void index(AgentDefinition agent);

    /**
     * Remove an agent from the query index.
     *
     * @return true if removed, false if not indexed
     */
    boolean removeIndex(String agentId);

    /**
     * Query agents by the given filter + pagination.
     *
     * @param query query parameters (status / nameContains / agentTier / capabilityTag / page / size)
     * @return paginated result of matching agents
     */
    PageResult<AgentDefinition> query(RepoQuery query);
}
