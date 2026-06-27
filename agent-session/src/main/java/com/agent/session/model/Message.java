package com.agent.session.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 消息实体（对齐 doc 01-database §1.2 session_message 表）。
 */
@Entity
@Table(name = "session_message",
        uniqueConstraints = @UniqueConstraint(name = "uk_msg_id", columnNames = "msg_id"),
        indexes = {
                @Index(name = "idx_session_step", columnList = "session_id,step_no")
        })
public class Message {

    @Id
    @Column(name = "msg_id", length = 32, nullable = false)
    private String msgId;

    @Column(name = "session_id", length = 32, nullable = false)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16, nullable = false)
    private MessageRole role;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    @Column(name = "content_type", length = 16, nullable = false)
    private String contentType = "text";

    @Column(name = "tool_calls", columnDefinition = "JSON")
    private String toolCalls;

    @Column(name = "tool_call_id", length = 64)
    private String toolCallId;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount = 0;

    @Column(name = "step_no")
    private Integer stepNo;

    @Column(name = "is_compressed", nullable = false)
    private Boolean isCompressed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.tokenCount == null) {
            this.tokenCount = 0;
        }
        if (this.isCompressed == null) {
            this.isCompressed = false;
        }
        if (this.contentType == null) {
            this.contentType = "text";
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public MessageRole getRole() {
        return role;
    }

    public void setRole(MessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(String toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public Integer getStepNo() {
        return stepNo;
    }

    public void setStepNo(Integer stepNo) {
        this.stepNo = stepNo;
    }

    public Boolean getIsCompressed() {
        return isCompressed;
    }

    public void setIsCompressed(Boolean isCompressed) {
        this.isCompressed = isCompressed;
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
