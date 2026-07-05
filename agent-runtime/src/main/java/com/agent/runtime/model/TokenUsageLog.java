package com.agent.runtime.model;

import com.agent.runtime.enums.ReActPhaseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Token 用量日志（doc 06-runtime §7.1，对应 token_usage_log 表）。
 *
 * <p>每次调用 ModelGateway 后追加一条记录，用于成本核算与水位监控。</p>
 */
@Entity
@Table(name = "token_usage_log")
public class TokenUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_instance_id", nullable = false, length = 64)
    private String agentInstanceId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "step_id", length = 64)
    private String stepId;

    /** ReAct 阶段（THINK / ACT / OBSERVE / REFLECT） */
    @Enumerated(EnumType.STRING)
    @Column(name = "phase", length = 16)
    private ReActPhaseType phase;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    /** 实际使用的模型（如 gpt-4o / claude-3.5-sonnet） */
    @Column(name = "model", length = 64)
    private String model;

    /** 单次调用成本（cent） */
    @Column(name = "cost_cent")
    private Long costCent;

    @Column(name = "logged_at", updatable = false)
    private Instant loggedAt;

    public TokenUsageLog() {
        this.loggedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAgentInstanceId() { return agentInstanceId; }
    public void setAgentInstanceId(String agentInstanceId) { this.agentInstanceId = agentInstanceId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public ReActPhaseType getPhase() { return phase; }
    public void setPhase(ReActPhaseType phase) { this.phase = phase; }

    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }

    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }

    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Long getCostCent() { return costCent; }
    public void setCostCent(Long costCent) { this.costCent = costCent; }

    public Instant getLoggedAt() { return loggedAt; }
    public void setLoggedAt(Instant loggedAt) { this.loggedAt = loggedAt; }
}
