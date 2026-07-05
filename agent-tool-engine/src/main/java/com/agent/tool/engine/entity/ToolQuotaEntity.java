package com.agent.tool.engine.entity;

import com.agent.tool.engine.enums.QuotaSubjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Tool quota JPA Entity (F8 工具配额).
 *
 * <p>Maps to {@code tool_quota} table (doc 01-database §4.3, DDL 04-agent-tool.sql).
 * 按 subject_type + subject_id + tool_id 维度限流, 日配额 + 日成本双控.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "tool_quota", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subject_tool",
                columnNames = {"subject_type", "subject_id", "tool_id"})
}, indexes = {
        @Index(name = "idx_subject", columnList = "subject_type,subject_id")
})
public class ToolQuotaEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JPA 主键. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 主体类型: tenant / agent / task. */
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 16)
    private QuotaSubjectType subjectType;

    /** 主体 ID. */
    @Column(name = "subject_id", nullable = false, length = 64)
    private String subjectId;

    /** 工具 ID（NULL=全工具配额）. */
    @Column(name = "tool_id", length = 32)
    private String toolId;

    /** 日调用量上限. */
    @Column(name = "daily_limit", nullable = false)
    private int dailyLimit;

    /** 已用调用量. */
    @Column(name = "daily_used", nullable = false)
    private int dailyUsed = 0;

    /** 日成本上限（分）. */
    @Column(name = "cost_limit_cent", nullable = false)
    private long costLimitCent;

    /** 已用成本（分）. */
    @Column(name = "cost_used_cent", nullable = false)
    private long costUsedCent = 0L;

    /** 下次重置时间. */
    @Column(name = "reset_at", nullable = false)
    private Instant resetAt;

    /** 创建时间. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 更新时间. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** 创建人. */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /** 更新人. */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    /** 逻辑删除. */
    @Column(name = "deleted", nullable = false)
    private int deleted = 0;

    /** JPA 乐观锁版本号. */
    @Version
    @Column(name = "version", nullable = false)
    private int version = 0;

    /** JPA 无参构造. */
    public ToolQuotaEntity() {
    }

    /** 业务全参构造. */
    public ToolQuotaEntity(QuotaSubjectType subjectType, String subjectId, String toolId,
                           int dailyLimit, long costLimitCent, Instant resetAt) {
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.toolId = toolId;
        this.dailyLimit = dailyLimit;
        this.costLimitCent = costLimitCent;
        this.resetAt = resetAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
