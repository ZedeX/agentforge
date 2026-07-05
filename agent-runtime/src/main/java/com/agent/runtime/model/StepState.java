package com.agent.runtime.model;

import com.agent.runtime.enums.ReActPhaseType;
import com.agent.runtime.enums.ReflexionResult;
import com.agent.runtime.enums.StepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * ReAct 循环单步状态（doc 06-runtime §7.1，对应 step_state 表）。
 *
 * <p>记录每个 THINK / ACT / OBSERVE / REFLECT 阶段的执行明细，支持断点续跑与审计回溯。
 * 字段对齐 doc 06 §7.1：step_id / session_id / tenant_id / agent_id / task_id / phase /
 * status / think_content / act_tool_id / act_params / observe_content / tokens_used /
 * reflexion_result / started_at / ended_at / duration_ms / error_message。</p>
 *
 * <p>向后兼容：保留旧构造器 {@code StepState(String agentInstanceId, int stepNumber, ReActPhaseType phase)}
 * 与 getter {@code getAgentId()} / {@code getStepNo()}（{@code @Transient} alias），
 * 供骨架阶段 StepStateSyncerImpl 内存实现使用。T7 落地 JPA 持久化时由 Repository 写入。</p>
 */
@Entity
@Table(name = "step_state")
public class StepState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 步骤唯一 ID（UUID，业务主键） */
    @Column(name = "step_id", nullable = false, length = 64, unique = true)
    private String stepId;

    /** Agent 实例 ID（proto agent_instance_id，对应 Redis runtime:{agentInstanceId}:state） */
    @Column(name = "agent_instance_id", nullable = false, length = 64)
    private String agentInstanceId;

    /** 会话 ID（关联 agent_session.session_id） */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    /** Agent 定义 ID（proto agent_id, int64）— 重命名为 agentDefinitionId 避免与 alias getAgentId() 冲突 */
    @Column(name = "agent_id")
    private Long agentDefinitionId;

    @Column(name = "task_id", length = 64)
    private String taskId;

    /** 步骤序号（同一 agent_instance_id 内递增） */
    @Column(name = "step_number")
    private Integer stepNumber;

    /** ReAct 阶段（THINK / ACT / OBSERVE / FINISH） */
    @Enumerated(EnumType.STRING)
    @Column(name = "phase", length = 16)
    private ReActPhaseType phase;

    /** 步骤状态（doc 06 §7.2，6 态） */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private StepStatus status;

    @Lob
    @Column(name = "think_content", columnDefinition = "TEXT")
    private String thinkContent;

    /** 工具调用类型（如 tool_id） */
    @Column(name = "action_type", length = 64)
    private String actionType;

    /** 工具调用目标（tool_id 或 endpoint） */
    @Column(name = "action_target", length = 128)
    private String actionTarget;

    @Lob
    @Column(name = "input_json", columnDefinition = "TEXT")
    private String inputJson;

    @Lob
    @Column(name = "output_json", columnDefinition = "TEXT")
    private String outputJson;

    @Lob
    @Column(name = "observe_content", columnDefinition = "TEXT")
    private String observeContent;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    /** 单步成本（cent） */
    @Column(name = "cost_cent")
    private Long costCent;

    @Enumerated(EnumType.STRING)
    @Column(name = "reflexion_result", length = 16)
    private ReflexionResult reflexionResult;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ============ 骨架阶段向后兼容字段（@Transient，不持久化） ============

    /**
     * 骨架阶段内存存储的检查点数据（StepStateSyncerImpl 用）。
     * T7 落地后会迁移到正式字段。
     */
    @Transient
    private String checkpointData;

    // ============ 构造器 ============

    public StepState() {
        this.stepId = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    /**
     * 骨架阶段兼容构造器（StepStateSyncerImpl / F6DecisionNodeTest 用）。
     *
     * @param agentInstanceId agent 实例 ID（旧名 agentId）
     * @param stepNumber      步骤序号（旧名 stepNo）
     * @param phase           ReAct 阶段
     */
    public StepState(String agentInstanceId, int stepNumber, ReActPhaseType phase) {
        this();
        this.agentInstanceId = agentInstanceId;
        this.stepNumber = stepNumber;
        this.phase = phase;
    }

    // ============ Getter / Setter ============

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getAgentInstanceId() { return agentInstanceId; }
    public void setAgentInstanceId(String agentInstanceId) { this.agentInstanceId = agentInstanceId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getAgentDefinitionId() { return agentDefinitionId; }
    public void setAgentDefinitionId(Long agentDefinitionId) { this.agentDefinitionId = agentDefinitionId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public Integer getStepNumber() { return stepNumber; }
    public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }

    public ReActPhaseType getPhase() { return phase; }
    public void setPhase(ReActPhaseType phase) { this.phase = phase; }

    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }

    public String getThinkContent() { return thinkContent; }
    public void setThinkContent(String thinkContent) { this.thinkContent = thinkContent; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getActionTarget() { return actionTarget; }
    public void setActionTarget(String actionTarget) { this.actionTarget = actionTarget; }

    public String getInputJson() { return inputJson; }
    public void setInputJson(String inputJson) { this.inputJson = inputJson; }

    public String getOutputJson() { return outputJson; }
    public void setOutputJson(String outputJson) { this.outputJson = outputJson; }

    public String getObserveContent() { return observeContent; }
    public void setObserveContent(String observeContent) { this.observeContent = observeContent; }

    public Integer getTokensUsed() { return tokensUsed; }
    public void setTokensUsed(Integer tokensUsed) { this.tokensUsed = tokensUsed; }

    public Long getCostCent() { return costCent; }
    public void setCostCent(Long costCent) { this.costCent = costCent; }

    public ReflexionResult getReflexionResult() { return reflexionResult; }
    public void setReflexionResult(ReflexionResult reflexionResult) { this.reflexionResult = reflexionResult; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // ============ 向后兼容 alias ============

    /**
     * 骨架阶段 alias：旧代码用 String agentId 表示 agent_instance_id。
     * @deprecated use {@link #getAgentInstanceId()}
     */
    @Deprecated
    @Transient
    public String getAgentId() { return agentInstanceId; }

    /**
     * @deprecated use {@link #setAgentInstanceId(String)}
     */
    @Deprecated
    public void setAgentId(String agentInstanceId) { this.agentInstanceId = agentInstanceId; }

    /**
     * 骨架阶段 alias：旧代码用 stepNo 表示 stepNumber。
     * @deprecated use {@link #getStepNumber()}
     */
    @Deprecated
    @Transient
    public int getStepNo() { return stepNumber == null ? 0 : stepNumber; }

    /**
     * @deprecated use {@link #setStepNumber(Integer)}
     */
    @Deprecated
    public void setStepNo(int stepNumber) { this.stepNumber = stepNumber; }

    public String getCheckpointData() { return checkpointData; }
    public void setCheckpointData(String checkpointData) { this.checkpointData = checkpointData; }
}
