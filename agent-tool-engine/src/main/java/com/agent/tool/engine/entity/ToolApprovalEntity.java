package com.agent.tool.engine.entity;

import com.agent.tool.engine.enums.ApprovalStatus;
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
 * Tool approval JPA Entity (F8 高危工具审批).
 *
 * <p>Maps to {@code tool_approval} table (doc 01-database §4.4, DDL 04-agent-tool.sql).
 * R3 高危工具限时授权记录, 状态机: PENDING -> APPROVED -> (expired after window).</p>
 */
@Getter
@Setter
@Entity
@Table(name = "tool_approval", uniqueConstraints = {
        @UniqueConstraint(name = "uk_approval_id", columnNames = "approval_id")
}, indexes = {
        @Index(name = "idx_apr_tool", columnList = "tool_id"),
        @Index(name = "idx_apr_task", columnList = "task_id"),
        @Index(name = "idx_apr_status", columnList = "status")
})
public class ToolApprovalEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JPA 主键. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 审批单 ID. */
    @Column(name = "approval_id", nullable = false, length = 32)
    private String approvalId;

    /** 工具 ID. */
    @Column(name = "tool_id", nullable = false, length = 32)
    private String toolId;

    /** 任务 ID. */
    @Column(name = "task_id", nullable = false, length = 32)
    private String taskId;

    /** Agent ID. */
    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    /** 入参快照（JSON 字符串）. */
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "JSON")
    private String inputSnapshot;

    /** 申请人. */
    @Column(name = "applicant", nullable = false, length = 64)
    private String applicant;

    /** 审批人. */
    @Column(name = "approver", length = 64)
    private String approver;

    /** 状态: pending / approved / rejected / expired. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    /** 过期时间（限时授权）. */
    @Column(name = "expire_at", nullable = false)
    private Instant expireAt;

    /** 申请理由. */
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    /** 审批意见. */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

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
    public ToolApprovalEntity() {
    }

    /** 业务全参构造. */
    public ToolApprovalEntity(String approvalId, String toolId, String taskId, Long agentId,
                              String inputSnapshot, String applicant, Instant expireAt) {
        this.approvalId = approvalId;
        this.toolId = toolId;
        this.taskId = taskId;
        this.agentId = agentId;
        this.inputSnapshot = inputSnapshot;
        this.applicant = applicant;
        this.expireAt = expireAt;
        this.status = ApprovalStatus.PENDING;
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
