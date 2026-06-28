package com.agent.orchestrator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 任务实例实体（对齐 doc 01-database §2.1 task_instance 表）。
 *
 * <p>包含 23 业务字段；审计字段 created_at / updated_at 继承自 {@link BaseEntity}。
 * 全参构造 {@link AllArgsConstructor} 覆盖 23 业务字段，对齐 DDL 字段顺序。</p>
 *
 * <p>风格参考 agent-session Session.java：JPA @Entity + @PrePersist 初始化默认值。
 * 与 Session 不同之处：本实体使用 Lombok @Data/@Builder/@NoArgsConstructor/@AllArgsConstructor
 * 简化样板代码，并继承 BaseEntity 复用审计字段。</p>
 */
@Entity
@Table(name = "task_instance",
        uniqueConstraints = @UniqueConstraint(name = "uk_task_id", columnNames = "task_id"),
        indexes = {
                @Index(name = "idx_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "idx_status", columnList = "status"),
                @Index(name = "idx_created_at", columnList = "created_at")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class TaskInstance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "task_id", length = 32, nullable = false, unique = true)
    private String taskId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "session_id", length = 32)
    private String sessionId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "goal", columnDefinition = "TEXT", nullable = false)
    private String goal;

    @Column(name = "complexity", nullable = false)
    private Integer complexity;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "task_schema", columnDefinition = "json", nullable = false)
    private String taskSchema;

    @Column(name = "dag_id")
    private Long dagId;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "parent_task_id", length = 32)
    private String parentTaskId;

    @Column(name = "replan_count", nullable = false)
    private int replanCount;

    @Column(name = "cost_limit_cent", nullable = false)
    private Long costLimitCent;

    @Column(name = "cost_used_cent", nullable = false)
    private Long costUsedCent;

    @Column(name = "token_used", nullable = false)
    private Integer tokenUsed;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_code", length = 32)
    private String errorCode;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @PrePersist
    void prePersist() {
        // 审计时间戳由 BaseEntity.touchTimestamps() 填充
        touchTimestamps();
        // 业务默认值
        if (this.priority == null) {
            this.priority = 5;
        }
        // replanCount 为基本类型 int，默认 0，无需 null 检查
        if (this.costUsedCent == null) {
            this.costUsedCent = 0L;
        }
        if (this.tokenUsed == null) {
            this.tokenUsed = 0;
        }
    }
}
