package com.agent.drift.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Memory record (F11 memory drift: invalid flag + expiry archive).
 */
public class MemoryRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String memoryId;
    private double relevanceScore;
    private boolean invalid;
    private Instant createdAt;
    private Instant expiredAt;
    private String archiveLocation;

    public MemoryRecord() {
    }

    public MemoryRecord(String memoryId, double relevanceScore) {
        this.memoryId = memoryId;
        this.relevanceScore = relevanceScore;
        this.createdAt = Instant.now();
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public void setExpiredAt(Instant expiredAt) {
        this.expiredAt = expiredAt;
    }

    public String getArchiveLocation() {
        return archiveLocation;
    }

    public void setArchiveLocation(String archiveLocation) {
        this.archiveLocation = archiveLocation;
    }
}
