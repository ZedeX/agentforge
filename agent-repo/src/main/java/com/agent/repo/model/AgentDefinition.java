package com.agent.repo.model;

import com.agent.repo.config.JsonListConverter;
import com.agent.repo.enums.AgentStatus;
import com.agent.repo.enums.AgentTier;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent definition (doc 01-database §6.1 agent_definition table, Plan 08 T2).
 *
 * <p>JPA Entity backing agent_definition table. Stores agent identity, ability tags,
 * system prompt, tier/limits, status state machine and bound tools/knowledge.
 * List&lt;String&gt; columns persisted as JSON string via {@link JsonListConverter}.</p>
 */
@Entity
@Table(name = "agent_definition", uniqueConstraints = @UniqueConstraint(name = "uk_agent_id", columnNames = "agent_id"))
public class AgentDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, length = 64, unique = true)
    private String agentId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", nullable = false, length = 65535)
    private String description;

    @Convert(converter = JsonListConverter.class)
    @Column(name = "ability_tags", nullable = false, length = 65535)
    private List<String> abilityTags = new ArrayList<>();

    @Column(name = "system_prompt", nullable = false, length = 65535)
    private String systemPrompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_tier", nullable = false, length = 16)
    private AgentTier agentTier = AgentTier.STANDARD;

    @Column(name = "max_steps", nullable = false)
    private int maxSteps = 10;

    @Column(name = "max_token", nullable = false)
    private int maxToken = 4096;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AgentStatus status = AgentStatus.DRAFT;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Convert(converter = JsonListConverter.class)
    @Column(name = "bound_tools", nullable = false, length = 65535)
    private List<String> boundTools = new ArrayList<>();

    @Convert(converter = JsonListConverter.class)
    @Column(name = "bound_knowledge_ids", nullable = false, length = 65535)
    private List<String> boundKnowledgeIds = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public AgentDefinition() {
    }

    public AgentDefinition(String agentId, String name) {
        this.agentId = agentId;
        this.name = name;
    }

    @PrePersist
    protected void onCreate() {
        long now = System.currentTimeMillis();
        if (createdAt == 0) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = System.currentTimeMillis();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
