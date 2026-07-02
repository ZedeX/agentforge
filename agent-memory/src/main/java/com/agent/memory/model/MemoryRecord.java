package com.agent.memory.model;

import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.enums.TaskOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Long-term memory record (F12 write / dedupe / TTL / distill unit).
 *
 * <p>JPA Entity mapping to {@code memory_record} table (doc 01-database §2.1,
 * Plan 03 T2). Stores structured metadata; vectors stored in Milvus.
 */
@Getter
@Setter
@Entity
@Table(name = "memory_record", uniqueConstraints = {
        @UniqueConstraint(name = "uk_memory_id", columnNames = "memory_id")
}, indexes = {
        @jakarta.persistence.Index(name = "idx_tenant_status", columnList = "tenant_id,status"),
        @jakarta.persistence.Index(name = "idx_topic", columnList = "topic"),
        @jakarta.persistence.Index(name = "idx_content_hash", columnList = "content_hash"),
        @jakarta.persistence.Index(name = "idx_ttl_expire", columnList = "ttl_expire_at")
})
public class MemoryRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JPA 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 记忆业务 ID（UUID）。 */
    @Column(name = "memory_id", nullable = false, length = 32)
    private String memoryId;

    /** 租户 ID。 */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /** 关联用户（情景记忆）。 */
    @Column(name = "user_id", length = 64)
    private String userId;

    /** 记忆类型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private MemoryType type;

    /** 记忆状态（RAW / ACTIVE / DISTILLED / ARCHIVED）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MemoryStatus status = MemoryStatus.RAW;

    /** 原始内容。 */
    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    /** 蒸馏后摘要。 */
    @Column(name = "summary", length = 512)
    private String summary;

    /** 主题。 */
    @Column(name = "topic", length = 128)
    private String topic;

    /** 关键词（JSON 数组字符串）。 */
    @Column(name = "keywords", length = 2048)
    private String keywords;

    /** 来源任务 ID。 */
    @Column(name = "source_task_id", length = 32)
    private String sourceTaskId;

    /** 任务结果（SUCCESS / FAILED / CANCELLED / TIMEOUT）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 16)
    private TaskOutcome outcome;

    /** 重要性评分 0~1。 */
    @Column(name = "importance_score", nullable = false)
    private double importanceScore;

    /** 重要性等级（HIGH / MEDIUM / LOW）。 */
    @Column(name = "importance_level", length = 8)
    private String importanceLevel;

    /** 内容 SHA-256 hash（去重用）。 */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /** Milvus 向量主键。 */
    @Column(name = "vector_id", length = 64)
    private String vectorId;

    /** 蒸馏来源父记忆 ID。 */
    @Column(name = "parent_memory_id", length = 32)
    private String parentMemoryId;

    /** 蒸馏产物子记忆 ID（JSON 数组字符串）。 */
    @Column(name = "child_memory_ids", length = 2048)
    private String childMemoryIds;

    /** TTL 过期时间。 */
    @Column(name = "ttl_expire_at")
    private Instant ttlExpireAt;

    /** 蒸馏次数。 */
    @Column(name = "distill_count")
    private int distillCount;

    /** 被召回次数。 */
    @Column(name = "recall_count")
    private int recallCount;

    /** 最近召回时间。 */
    @Column(name = "last_recalled_at")
    private Instant lastRecalledAt;

    /** 元数据（JSON 字符串）。 */
    @Column(name = "metadata", length = 4096)
    private String metadata;

    /** 创建时间。 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 更新时间。 */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** JPA 无参构造（规范要求）。 */
    public MemoryRecord() {
    }

    /** 业务全参构造（方便业务代码使用）。 */
    public MemoryRecord(String memoryId, MemoryType type, String content) {
        this.memoryId = memoryId;
        this.type = type;
        this.content = content;
        this.status = MemoryStatus.RAW;
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
