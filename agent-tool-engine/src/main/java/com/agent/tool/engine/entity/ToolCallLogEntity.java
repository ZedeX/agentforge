package com.agent.tool.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Tool call log JPA Entity (F8 工具调用日志).
 *
 * <p>Maps to {@code tool_call_log} table (doc 01-database §4.2, DDL 04-agent-tool.sql).
 * 按月分表: tool_call_log_YYYYMM (ShardingSphere 分片键 task_id + created_at).</p>
 */
@Getter
@Setter
@Entity
@Table(name = "tool_call_log", uniqueConstraints = {
        @UniqueConstraint(name = "uk_call_id", columnNames = "call_id")
}, indexes = {
        @Index(name = "idx_log_task_step", columnList = "task_id,step_no"),
        @Index(name = "idx_log_tool_status", columnList = "tool_id,status"),
        @Index(name = "idx_log_created", columnList = "created_at"),
        @Index(name = "idx_log_risk_level", columnList = "risk_level")
})
public class ToolCallLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JPA 主键. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 调用 ID. */
    @Column(name = "call_id", nullable = false, length = 32)
    private String callId;

    /** 任务 ID（分片键）. */
    @Column(name = "task_id", nullable = false, length = 32)
    private String taskId;

    /** 步骤号. */
    @Column(name = "step_no")
    private Integer stepNo;

    /** Agent ID. */
    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    /** 工具 ID. */
    @Column(name = "tool_id", nullable = false, length = 32)
    private String toolId;

    /** 工具版本. */
    @Column(name = "tool_version", nullable = false)
    private int toolVersion;

    /** 入参快照（脱敏后, JSON 字符串）. */
    @Column(name = "input", nullable = false, columnDefinition = "TEXT")
    private String input;

    /** 输出快照（截断, JSON 字符串）. */
    @Column(name = "output", columnDefinition = "TEXT")
    private String output;

    /** 状态: success / failed / timeout / blocked. */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 错误码. */
    @Column(name = "error_code", length = 32)
    private String errorCode;

    /** 错误信息. */
    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    /** 耗时（毫秒）. */
    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    /** 成本（分）. */
    @Column(name = "cost_cent", nullable = false)
    private long costCent;

    /** Token 用量. */
    @Column(name = "token_used", nullable = false)
    private int tokenUsed = 0;

    /** 风险等级. */
    @Column(name = "risk_level", nullable = false)
    private int riskLevel;

    /** 审批人（R3）. */
    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    /** 链路 ID. */
    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    /** 租户 ID (T9 audit field). */
    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    /** 入参 hash (T9 audit field, ParamsHasher 64-char hex). */
    @Column(name = "params_hash", length = 64)
    private String paramsHash;

    /** 调用开始时间 (T9 audit field). */
    @Column(name = "started_at")
    private Instant startedAt;

    /** 调用结束时间 (T9 audit field). */
    @Column(name = "ended_at")
    private Instant endedAt;

    /** 退出码 (T9 audit field, sandbox exec exit code). */
    @Column(name = "exit_code")
    private Integer exitCode;

    /** 沙箱容器 ID (T9 audit field, null for non-sandbox executors). */
    @Column(name = "sandbox_container_id", length = 64)
    private String sandboxContainerId;

    /** 是否命中缓存 (T9 audit field). */
    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit = false;

    /** 创建时间. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA 无参构造. */
    public ToolCallLogEntity() {
    }

    /** 业务全参构造. */
    public ToolCallLogEntity(String callId, String taskId, Long agentId, String toolId,
                             int toolVersion, String input, String status, int durationMs,
                             int riskLevel, String traceId) {
        this.callId = callId;
        this.taskId = taskId;
        this.agentId = agentId;
        this.toolId = toolId;
        this.toolVersion = toolVersion;
        this.input = input;
        this.status = status;
        this.durationMs = durationMs;
        this.riskLevel = riskLevel;
        this.traceId = traceId;
        this.costCent = 0L;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
