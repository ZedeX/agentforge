package com.agent.quality.model;

import com.agent.quality.enums.BadcaseSeverity;

import java.time.Instant;

/**
 * 人工审核队列条目 (doc 11-detail-flow F9, PRD §四(二)4).
 *
 * <p>高严重度 Badcase (severityScore ≥ 0.8) 推送至人工审核队列,
 * 由审核人员填写 reviewResult 后回写.</p>
 */
public class ManualReviewItem {

    private String reviewId;
    private String badcaseId;
    private BadcaseSeverity severity;
    private String reviewer;
    private String reviewResult;
    private Instant enqueuedAt;

    public ManualReviewItem() {
    }

    public ManualReviewItem(String badcaseId, BadcaseSeverity severity) {
        this.badcaseId = badcaseId;
        this.severity = severity;
        this.enqueuedAt = Instant.now();
    }

    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public String getBadcaseId() { return badcaseId; }
    public void setBadcaseId(String badcaseId) { this.badcaseId = badcaseId; }

    public BadcaseSeverity getSeverity() { return severity; }
    public void setSeverity(BadcaseSeverity severity) { this.severity = severity; }

    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }

    public String getReviewResult() { return reviewResult; }
    public void setReviewResult(String reviewResult) { this.reviewResult = reviewResult; }

    public Instant getEnqueuedAt() { return enqueuedAt; }
    public void setEnqueuedAt(Instant enqueuedAt) { this.enqueuedAt = enqueuedAt; }
}
