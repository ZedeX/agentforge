package com.agent.hallucination.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 幻觉率指标 JPA Entity（F10 L6: agent_metrics_daily 指标落库）。
 *
 * <p>映射 {@code hallucination_metric} 表，按 (tenant_id, agent_id, stat_date) 聚合指标。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "hallucination_metric", indexes = {
        @Index(name = "idx_tenant_agent_date", columnList = "tenant_id,agent_id,stat_date"),
        @Index(name = "idx_stat_date", columnList = "stat_date")
})
public class HallucinationMetricEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JPA 主键（自增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 指标业务 ID（UUID）。 */
    @Column(name = "metric_id", nullable = false, length = 36)
    private String metricId;

    /** 租户 ID。 */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /** Agent ID。 */
    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    /** 统计日期。 */
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    /** 幻觉率 (hallucination_count / total_claims)。 */
    @Column(name = "hallucination_rate", nullable = false)
    private double hallucinationRate;

    /** 总 claim 数。 */
    @Column(name = "total_claims", nullable = false)
    private long totalClaims;

    /** 幻觉计数。 */
    @Column(name = "hallucination_count", nullable = false)
    private long hallucinationCount;

    /** 检测层（self_check / tool_guard / rag_anchor / hard_validator）。 */
    @Column(name = "layer", length = 32)
    private String layer;

    /** 事件类型（detected / prevented / false_positive）。 */
    @Column(name = "event_type", length = 32)
    private String eventType;

    /** 事件详情。 */
    @Column(name = "detail", length = 2048)
    private String detail;

    /** 关联任务 ID。 */
    @Column(name = "task_id", length = 36)
    private String taskId;

    /** 创建时间。 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 更新时间。 */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** JPA 无参构造（规范要求）。 */
    public HallucinationMetricEntity() {
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
