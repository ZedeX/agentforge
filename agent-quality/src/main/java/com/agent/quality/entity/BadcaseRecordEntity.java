package com.agent.quality.entity;

import com.agent.quality.enums.BadcaseCategory;
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
 * Badcase 记录 JPA Entity（对齐 quality.proto ReportBadcaseRequest）。
 *
 * <p>L4 校验失败且 retry_count > max 后写入 badcase 表。
 * severityScore >= threshold 时需推送人工审核队列。</p>
 */
@Entity
@Table(name = "badcase_record", uniqueConstraints = {
        @UniqueConstraint(name = "uk_badcase_id", columnNames = "badcase_id")
}, indexes = {
        @jakarta.persistence.Index(name = "idx_task_id", columnList = "task_id"),
        @jakarta.persistence.Index(name = "idx_category", columnList = "category")
})
public class BadcaseRecordEntity {

    /** JPA 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Badcase 业务 ID（唯一，如 bc-1234567890）。 */
    @Column(name = "badcase_id", nullable = false, unique = true, length = 64)
    private String badcaseId;

    /** 关联任务 ID。 */
    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    /** Badcase 归类。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private BadcaseCategory category;

    /** 严重度。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private BadcaseSeverity severity;

    /** Badcase 内容 / 描述。 */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 失败原因。 */
    @Column(name = "failure_reason", length = 1024)
    private String failureReason;

    /** 严重度评分 [0, 1]。 */
    @Column(name = "severity_score", nullable = false)
    private double severityScore;

    /** 创建时间。 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA 无参构造（规范要求）。 */
    public BadcaseRecordEntity() {
    }

    /** 业务全参构造。 */
    public BadcaseRecordEntity(String badcaseId, String taskId, BadcaseCategory category,
                                BadcaseSeverity severity, String content,
                                String failureReason, double severityScore) {
        this.badcaseId = badcaseId;
        this.taskId = taskId;
        this.category = category;
        this.severity = severity;
        this.content = content;
        this.failureReason = failureReason;
        this.severityScore = severityScore;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ===== getters & setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
