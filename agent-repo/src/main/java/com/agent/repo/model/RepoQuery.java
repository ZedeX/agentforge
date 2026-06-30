package com.agent.repo.model;

import com.agent.repo.enums.AgentStatus;
import com.agent.repo.enums.AgentTier;
import com.agent.repo.enums.CapabilityTag;

/**
 * Repo query parameters (doc 06-agent-repo §5 ListAgents RPC).
 *
 * <p>Carries filter (status / nameContains / tier / capabilityTag) + pagination (page / size).
 * Skeleton stage: in-memory POJO.</p>
 */
public class RepoQuery {

    private AgentStatus status;
    private String nameContains;
    private AgentTier agentTier;
    private CapabilityTag capabilityTag;
    private int page = 0;
    private int size = 10;

    public RepoQuery() {
    }

    public AgentStatus getStatus() { return status; }
    public void setStatus(AgentStatus status) { this.status = status; }

    public String getNameContains() { return nameContains; }
    public void setNameContains(String nameContains) { this.nameContains = nameContains; }

    public AgentTier getAgentTier() { return agentTier; }
    public void setAgentTier(AgentTier agentTier) { this.agentTier = agentTier; }

    public CapabilityTag getCapabilityTag() { return capabilityTag; }
    public void setCapabilityTag(CapabilityTag capabilityTag) { this.capabilityTag = capabilityTag; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
