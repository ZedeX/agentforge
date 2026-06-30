package com.agent.repo.model;

import com.agent.repo.enums.AgentStatus;
import com.agent.repo.enums.AgentTier;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent definition (doc 01-database §6.1 agent_definition table).
 *
 * <p>Skeleton stage: in-memory POJO. JPA Entity annotation deferred to Plan 08 T2 deepening.</p>
 */
public class AgentDefinition {

    private String agentId;
    private String name;
    private String description;
    private List<String> abilityTags = new ArrayList<>();
    private String systemPrompt;
    private AgentTier agentTier = AgentTier.STANDARD;
    private int maxSteps = 10;
    private int maxToken = 4096;
    private AgentStatus status = AgentStatus.DRAFT;
    private int version = 1;
    private List<String> boundTools = new ArrayList<>();
    private List<String> boundKnowledgeIds = new ArrayList<>();
    private long createdAt;
    private long updatedAt;

    public AgentDefinition() {
    }

    public AgentDefinition(String agentId, String name) {
        this.agentId = agentId;
        this.name = name;
    }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getAbilityTags() { return abilityTags; }
    public void setAbilityTags(List<String> abilityTags) {
        this.abilityTags = abilityTags != null ? abilityTags : new ArrayList<>();
    }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public AgentTier getAgentTier() { return agentTier; }
    public void setAgentTier(AgentTier agentTier) { this.agentTier = agentTier; }

    public int getMaxSteps() { return maxSteps; }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }

    public int getMaxToken() { return maxToken; }
    public void setMaxToken(int maxToken) { this.maxToken = maxToken; }

    public AgentStatus getStatus() { return status; }
    public void setStatus(AgentStatus status) { this.status = status; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public List<String> getBoundTools() { return boundTools; }
    public void setBoundTools(List<String> boundTools) {
        this.boundTools = boundTools != null ? boundTools : new ArrayList<>();
    }

    public List<String> getBoundKnowledgeIds() { return boundKnowledgeIds; }
    public void setBoundKnowledgeIds(List<String> boundKnowledgeIds) {
        this.boundKnowledgeIds = boundKnowledgeIds != null ? boundKnowledgeIds : new ArrayList<>();
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
