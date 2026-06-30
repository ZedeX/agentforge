package com.agent.repo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Agent user rating (doc 06-agent-repo §3.2 agent_rating table, Plan 08 T4).
 *
 * <p>JPA Entity backing agent_rating table. Each rating has a score [1,5] + comment,
 * submitted by a user for a specific agentId.</p>
 */
@Entity
@Table(name = "agent_rating")
public class AgentRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "comment", length = 65535)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private long createdAt;

    public AgentRating() {
    }

    public AgentRating(String agentId, String userId, int score, String comment) {
        this.agentId = agentId;
        this.userId = userId;
        this.score = score;
        this.comment = comment;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == 0) {
            createdAt = System.currentTimeMillis();
        }
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
