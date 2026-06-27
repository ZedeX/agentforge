package com.agent.session.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 会话实体（对齐 doc 01-database §1.1 session 表）。
 *
 * 注：DB 中 id 为 BIGINT 雪花主键，session_id 为业务 ID（UUID 去横线）。
 *     实体采用 session_id 作为 @Id（业务主键策略，便于跨服务调用），
 *     雪花 id 通过 dbId 字段映射（可选）。
 */
@Entity
@Table(name = "session",
        uniqueConstraints = @UniqueConstraint(name = "uk_session_id", columnNames = "session_id"),
        indexes = {
                @Index(name = "idx_tenant_user_status", columnList = "tenant_id,user_id,status"),
                @Index(name = "idx_last_msg", columnList = "last_msg_at")
        })
public class Session {

    @Id
    @Column(name = "session_id", length = 32, nullable = false)
    private String sessionId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "last_msg_at")
    private Instant lastMsgAt;

    @Column(name = "token_used", nullable = false)
    private Long tokenUsed = 0L;

    @Column(name = "context_summary", columnDefinition = "TEXT")
    private String contextSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = SessionStatus.ACTIVE.getCode();
        }
        if (this.tokenUsed == null) {
            this.tokenUsed = 0L;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Instant getLastMsgAt() {
        return lastMsgAt;
    }

    public void setLastMsgAt(Instant lastMsgAt) {
        this.lastMsgAt = lastMsgAt;
    }

    public Long getTokenUsed() {
        return tokenUsed;
    }

    public void setTokenUsed(Long tokenUsed) {
        this.tokenUsed = tokenUsed;
    }

    public String getContextSummary() {
        return contextSummary;
    }

    public void setContextSummary(String contextSummary) {
        this.contextSummary = contextSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
