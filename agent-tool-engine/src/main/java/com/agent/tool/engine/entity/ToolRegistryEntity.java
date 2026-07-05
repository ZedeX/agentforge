package com.agent.tool.engine.entity;

import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.enums.ToolStatus;
import com.agent.tool.engine.enums.ToolType;
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
 * Tool registry JPA Entity (F8 工具注册表).
 *
 * <p>Maps to {@code tool_registry} table (doc 01-database §4.1, DDL 04-agent-tool.sql).
 * 存储工具元数据、Schema、风险等级、执行器类型等完整注册信息.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "tool_registry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tool_id", columnNames = "tool_id"),
        @UniqueConstraint(name = "uk_name_version", columnNames = {"name", "version"})
}, indexes = {
        @Index(name = "idx_reg_risk_level", columnList = "risk_level"),
        @Index(name = "idx_reg_status", columnList = "status")
})
public class ToolRegistryEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JPA 主键（雪花算法生成, IDENTITY 策略兼容 MySQL BIGINT UNSIGNED）. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 工具业务 ID. */
    @Column(name = "tool_id", nullable = false, length = 32)
    private String toolId;

    /** 工具名（唯一）. */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 显示名. */
    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    /** 功能描述（供模型召回）. */
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /** 场景标签（JSON 数组字符串）. */
    @Column(name = "scene_tags", nullable = false, columnDefinition = "JSON")
    private String sceneTags;

    /** 能力标签数组（JSON 数组字符串, 用于 Agent 匹配）. */
    @Column(name = "ability_tags", columnDefinition = "JSON")
    private String abilityTags;

    /** 工具类型: atomic / composite / agent. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tool_type", nullable = false, length = 16)
    private ToolType toolType;

    /** 风险等级: 1=R1低 2=R2中 3=R3高. */
    @Column(name = "risk_level", nullable = false)
    private int riskLevel;

    /** 输入参数 JSON Schema. */
    @Column(name = "input_schema", nullable = false, columnDefinition = "JSON")
    private String inputSchema;

    /** 输出结构定义. */
    @Column(name = "output_schema", nullable = false, columnDefinition = "JSON")
    private String outputSchema;

    /** 错误码规范（JSON 数组字符串）. */
    @Column(name = "error_codes", nullable = false, columnDefinition = "JSON")
    private String errorCodes;

    /** 执行器类型: general / proxy / sandbox. */
    @Enumerated(EnumType.STRING)
    @Column(name = "executor_type", nullable = false, length = 16)
    private ExecutorType executorType;

    /** 调用地址（gRPC service/method 或 HTTP URL）. */
    @Column(name = "endpoint", nullable = false, length = 255)
    private String endpoint;

    /** 默认超时（毫秒）. */
    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    /** 平均成本（分）. */
    @Column(name = "avg_cost_cent", nullable = false)
    private long avgCostCent = 0L;

    /** 平均耗时（毫秒）. */
    @Column(name = "avg_duration_ms", nullable = false)
    private int avgDurationMs = 0;

    /** 补偿动作定义（JSON, 写操作必填）. */
    @Column(name = "undo_action", columnDefinition = "JSON")
    private String undoAction;

    /** Prompt 缓存键. */
    @Column(name = "prompt_cache_key", length = 128)
    private String promptCacheKey;

    /** 状态: 1=草稿 2=启用 3=下线. */
    @Column(name = "status", nullable = false)
    private int status = 2;

    /** 版本号. */
    @Column(name = "version", nullable = false)
    private int version = 1;

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

    /** 逻辑删除: 0=未删 1=已删. */
    @Column(name = "deleted", nullable = false)
    private int deleted = 0;

    /** JPA 乐观锁版本号. */
    @Version
    @Column(name = "version_lock", nullable = false)
    private int versionLock = 0;

    /** JPA 无参构造（规范要求）. */
    public ToolRegistryEntity() {
    }

    /** 业务全参构造（方便注册时使用）. */
    public ToolRegistryEntity(String toolId, String name, String displayName, String description,
                              ToolType toolType, int riskLevel, ExecutorType executorType,
                              String endpoint, int timeoutMs) {
        this.toolId = toolId;
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.toolType = toolType;
        this.riskLevel = riskLevel;
        this.executorType = executorType;
        this.endpoint = endpoint;
        this.timeoutMs = timeoutMs;
        this.status = ToolStatus.ENABLED.getCode();
        this.version = 1;
    }

    /** 便捷方法: 获取风险等级枚举. */
    public ToolRiskLevel getRiskLevelEnum() {
        return ToolRiskLevel.fromLevel(riskLevel);
    }

    /** 便捷方法: 设置风险等级枚举. */
    public void setRiskLevelEnum(ToolRiskLevel level) {
        this.riskLevel = level.getLevel();
    }

    /** 便捷方法: 获取状态枚举. */
    public ToolStatus getStatusEnum() {
        return ToolStatus.fromCode(status);
    }

    /** 便捷方法: 设置状态枚举. */
    public void setStatusEnum(ToolStatus toolStatus) {
        this.status = toolStatus.getCode();
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
