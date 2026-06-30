package com.agent.repo.model;

/**
 * Agent user rating (doc 06-agent-repo §3.2 agent_rating table, skeleton).
 *
 * <p>Each rating has a score [1,5] + comment, submitted by a user for a specific agentId.
 * Skeleton stage: in-memory POJO. JPA Entity deferred to Plan 08 deepening.</p>
 */
public class AgentRating {

    private Long id;
    private String agentId;
    private String userId;
    private int score;
    private String comment;
    private long createdAt;

    public AgentRating() {
    }

    public AgentRating(String agentId, String userId, int score, String comment) {
        this.agentId = agentId;
        this.userId = userId;
        this.score = score;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
