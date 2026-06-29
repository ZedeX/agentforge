package com.agent.memory.model;

import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;

import java.io.Serializable;
import java.time.Instant;

/**
 * Long-term memory record (F12 write / dedupe / TTL unit).
 */
public class MemoryRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String memoryId;
    private MemoryType type;
    private String content;
    private double importanceScore;
    private String source;
    private int accessCount;
    private Instant createdAt;
    private Instant lastAccessedAt;
    private MemoryStatus status = MemoryStatus.HOT;
    /** TTL in days (default 90). */
    private int ttlDays = 90;
    private String topic;

    public MemoryRecord() {
    }

    public MemoryRecord(String memoryId, MemoryType type, String content) {
        this.memoryId = memoryId;
        this.type = type;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public MemoryType getType() {
        return type;
    }

    public void setType(MemoryType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getImportanceScore() {
        return importanceScore;
    }

    public void setImportanceScore(double importanceScore) {
        this.importanceScore = importanceScore;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public MemoryStatus getStatus() {
        return status;
    }

    public void setStatus(MemoryStatus status) {
        this.status = status;
    }

    public int getTtlDays() {
        return ttlDays;
    }

    public void setTtlDays(int ttlDays) {
        this.ttlDays = ttlDays;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
}
