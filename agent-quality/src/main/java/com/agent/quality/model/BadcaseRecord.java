package com.agent.quality.model;

import com.agent.quality.enums.BadcaseCategory;
import com.agent.quality.enums.BadcaseSeverity;

import java.time.Instant;

/**
 * Badcase 记录 (doc 11-detail-flow F9, PRD §四(二)4).
 *
 * <p>L4 校验失败且 retry_count &gt; max 后写入 badcase 表.
 * severityScore ≥ 0.8 时需推送人工审核队列.</p>
 */
public class BadcaseRecord {

    private String badcaseId;
    private String taskId;
    private BadcaseCategory category;
    private BadcaseSeverity severity;
    private String content;
    private String failureReason;
    private double severityScore;
    private Instant createdAt;

    public BadcaseRecord() {
    }

    public BadcaseRecord(String badcaseId, String taskId, BadcaseCategory category) {
        this.badcaseId = badcaseId;
        this.taskId = taskId;
        this.category = category;
        this.createdAt = Instant.now();
    }

    public String getBadcaseId() { return badcaseId; }
    public void setBadcaseId(String badcaseId) { this.badcaseId = badcaseId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public BadcaseCategory getCategory() { return category; }
    public void setCategory(BadcaseCategory category) { this.category = category; }

    public BadcaseSeverity getSeverity() { return severity; }
    public void setSeverity(BadcaseSeverity severity) { this.severity = severity; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public double getSeverityScore() { return severityScore; }
    public void setSeverityScore(double severityScore) { this.severityScore = severityScore; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
