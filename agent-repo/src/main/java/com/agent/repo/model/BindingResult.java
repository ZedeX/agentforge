package com.agent.repo.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of binding tools / knowledge to an Agent (doc 06-agent-repo §4.3).
 *
 * <p>Returned by AgentRepository.bindTools / bindKnowledge methods to carry the post-bind
 * state of the bound asset lists.</p>
 */
public class BindingResult {

    private final String agentId;
    private final List<String> boundTools;
    private final List<String> boundKnowledgeIds;
    private final boolean success;

    public BindingResult(String agentId, List<String> boundTools, List<String> boundKnowledgeIds, boolean success) {
        this.agentId = agentId;
        this.boundTools = boundTools != null ? new ArrayList<>(boundTools) : new ArrayList<>();
        this.boundKnowledgeIds = boundKnowledgeIds != null ? new ArrayList<>(boundKnowledgeIds) : new ArrayList<>();
        this.success = success;
    }

    public String getAgentId() { return agentId; }

    public List<String> getBoundTools() { return boundTools; }

    public List<String> getBoundKnowledgeIds() { return boundKnowledgeIds; }

    public boolean isSuccess() { return success; }
}
