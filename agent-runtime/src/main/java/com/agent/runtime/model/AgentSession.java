package com.agent.runtime.model;

import com.agent.runtime.enums.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Agent 会话实体（doc 06-runtime §7.1，对应 agent_session 表）。
 *
 * <p>记录一次 StartAgent 调用产生的会话生命周期信息：
 * agent_instance_id（业务主键）/ agent_id / task_id / subtask_id / node_id /
 * 状态 / 步骤计数 / token 预算 / 成本预算 / 开始/结束时间等。</p>
 *
 * <p>对齐 proto StartAgentRequest / AgentState 字段。</p>
 */
@Entity
@Table(name = "agent_session")
public class AgentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Agent 实例 ID（业务主键，proto agent_instance_id） */
    @Column(name = "agent_instance_id", nullable = false, length = 64, unique = true)
    private String agentInstanceId;

    /** 会话 ID（与 agent_instance_id 一致，保留为 alias 便于查询） */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    /** Agent 定义 ID（proto agent_id, int64） */
    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    /** Agent 版本（proto agent_version） */
    @Column(name = "agent_version")
    private Integer agentVersion;

    @Column(name = "task_id", length = 64)
    private String taskId;

    /** 子任务 ID（proto subtask_id） */
    @Column(name = "subtask_id", length = 64)
    private String subtaskId;

    /** 节点 ID（proto node_id，task-orchestrator 流程节点） */
    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private SessionStatus status;

    @Column(name = "current_step_number")
    private Integer currentStepNumber;

    @Column(name = "max_steps", nullable = false)
    private Integer maxSteps;

    @Column(name = "token_budget", nullable = false)
    private Integer tokenBudget;

    @Column(name = "token_used")
    private Integer tokenUsed;

    @Column(name = "cost_budget_cent")
    private Long costBudgetCent;

    @Column(name = "cost_used_cent")
    private Long costUsedCent;

    @Lob
    @Column(name = "inputs_json", columnDefinition = "TEXT")
    private String inputsJson;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /** 暂停时间戳（Pause RPC 触发） */
    @Column(name = "paused_at")
    private Instant pausedAt;

    /** 恢复时间戳（Resume RPC 触发） */
    @Column(name = "resumed_at")
    private Instant resumedAt;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "updated_at")
    private Instant updatedAt;

    public AgentSession() {
        this.agentInstanceId = UUID.randomUUID().toString();
        this.sessionId = this.agentInstanceId;
        this.status = SessionStatus.CREATING;
        this.currentStepNumber = 0;
        this.tokenUsed = 0;
        this.costUsedCent = 0L;
        this.createdAt = Instant.now();
    }

    // ============ Getter / Setter ============

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAgentInstanceId() { return agentInstanceId; }
    public void setAgentInstanceId(String agentInstanceId) { this.agentInstanceId = agentInstanceId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public Integer getAgentVersion() { return agentVersion; }
    public void setAgentVersion(Integer agentVersion) { this.agentVersion = agentVersion; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getSubtaskId() { return subtaskId; }
    public void setSubtaskId(String subtaskId) { this.subtaskId = subtaskId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public Integer getCurrentStepNumber() { return currentStepNumber; }
    public void setCurrentStepNumber(Integer currentStepNumber) { this.currentStepNumber = currentStepNumber; }

    public Integer getMaxSteps() { return maxSteps; }
    public void setMaxSteps(Integer maxSteps) { this.maxSteps = maxSteps; }

    public Integer getTokenBudget() { return tokenBudget; }
    public void setTokenBudget(Integer tokenBudget) { this.tokenBudget = tokenBudget; }

    public Integer getTokenUsed() { return tokenUsed; }
    public void setTokenUsed(Integer tokenUsed) { this.tokenUsed = tokenUsed; }

    public Long getCostBudgetCent() { return costBudgetCent; }
    public void setCostBudgetCent(Long costBudgetCent) { this.costBudgetCent = costBudgetCent; }

    public Long getCostUsedCent() { return costUsedCent; }
    public void setCostUsedCent(Long costUsedCent) { this.costUsedCent = costUsedCent; }

    public String getInputsJson() { return inputsJson; }
    public void setInputsJson(String inputsJson) { this.inputsJson = inputsJson; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public Instant getPausedAt() { return pausedAt; }
    public void setPausedAt(Instant pausedAt) { this.pausedAt = pausedAt; }

    public Instant getResumedAt() { return resumedAt; }
    public void setResumedAt(Instant resumedAt) { this.resumedAt = resumedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
