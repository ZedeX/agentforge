package com.agent.quality.entity;

import com.agent.quality.enums.BadcaseSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 人工审核队列条目 JPA Entity（对齐 quality.proto ReviewItem）。
 *
 * <p>高严重度 Badcase (severityScore >= threshold) 推送至人工审核队列,
 * 由审核人员填写 reviewResult 后回写。</p>
 */
@Entity
@Table(name = "review_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_review_id", columnNames = "review_id")
}, indexes = {
        @jakarta.persistence.Index(name = "idx_status_enqueued", columnList = "status,enqueued_at"),
        @jakarta.persistence.Index(name = "idx_badcase_id", columnList = "badcase_id")
})
public class ReviewItemEntity {

    /** JPA 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 审核条目业务 ID（唯一）。 */
    @Column(name = "review_id", nullable = false, unique = true, length = 64)
    private String reviewId;

    /** 关联 Badcase ID。 */
    @Column(name = "badcase_id", nullable = false, length = 64)
    private String badcaseId;

    /** 严重度。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private BadcaseSeverity severity;

    /** 审核人员。 */
    @Column(name = "reviewer", length = 64)
    private String reviewer;

    /** 审核结果。 */
    @Column(name = "review_result", length = 256)
    private String reviewResult;

    /** 审核状态：pending / in_review / resolved。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "pending";

    /** 入队时间。 */
    @Column(name = "enqueued_at", nullable = false, updatable = false)
    private Instant enqueuedAt;

    /** 解决时间。 */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** JPA 无参构造（规范要求）。 */
    public ReviewItemEntity() {
    }

    @PrePersist
    void onCreate() {
        if (enqueuedAt == null) {
            enqueuedAt = Instant.now();
        }
    }

    // ===== getters & setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getEnqueuedAt() { return enqueuedAt; }
    public void setEnqueuedAt(Instant enqueuedAt) { this.enqueuedAt = enqueuedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
