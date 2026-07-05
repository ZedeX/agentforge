package com.agent.runtime.model;

import com.agent.runtime.enums.ReActPhaseType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ReAct 循环执行上下文（doc 06-runtime §2 / §9.1，F6 think/act/observe/finish）。
 *
 * <p>2026-07-05 T3 升级：补充 sessionId / agentInstanceId / maxSteps / cancelled /
 * tokenUsed / costUsedCent / retryCount 字段，支撑 Plan 06 T3 完整循环。</p>
 */
public class ReActContext {

    /** Agent 实例 ID（业务主键，proto agent_instance_id） */
    private String agentInstanceId;
    /** 会话 ID（与 agent_instance_id 一致） */
    private String sessionId;
    /** 旧字段，与 agentInstanceId 同义（骨架兼容） */
    private String agentId;
    private String taskId;
    private String subtaskId;
    private String tenantId;
    private Long agentDefinitionId;
    private ReActPhaseType phase;
    /** 循环计数（每完成一个 Think 算一轮） */
    private int loopCount;
    /** 当前步骤序号 */
    private int stepNumber;
    /** 最大步数（超限触发 ABORT，默认 20） */
    private int maxSteps = 20;
    /** token 预算 */
    private int tokenBudget = 32000;
    /** 已用 token */
    private int tokenUsed = 0;
    /** 已用成本（cent） */
    private long costUsedCent = 0;
    /** 取消信号（外部线程设置 true 中断循环） */
    private volatile boolean cancelled = false;
    /** 当前重试计数（Reflexion RETRY 触发） */
    private int retryCount = 0;
    /** 最大重试次数（超过则 ABORT） */
    private int maxRetry = 2;
    /** 最终答案 */
    private String finalAnswer;
    /** 用户原始输入 */
    private String userInput;
    /** 循环内存（注入 recalled memory / tool result 等） */
    private Map<String, Object> memory;

    public ReActContext() {
        this.memory = new HashMap<>();
        this.phase = ReActPhaseType.THINK;
        this.loopCount = 0;
        this.stepNumber = 0;
    }

    /** 骨架阶段兼容构造器（旧字段 agentId 现表示 agentInstanceId） */
    public ReActContext(String agentId, String taskId) {
        this();
        this.agentInstanceId = agentId;
        this.agentId = agentId;
        this.sessionId = agentId;
        this.taskId = taskId;
    }

    /** T3 新构造器（含 sessionId / maxSteps / tokenBudget） */
    public ReActContext(String agentInstanceId, String sessionId, String taskId,
                        int maxSteps, int tokenBudget) {
        this();
        this.agentInstanceId = agentInstanceId;
        this.agentId = agentInstanceId;
        this.sessionId = sessionId != null ? sessionId : agentInstanceId;
        this.taskId = taskId;
        this.maxSteps = maxSteps;
        this.tokenBudget = tokenBudget;
    }

    // ============ Getter / Setter ============

    public String getAgentInstanceId() { return agentInstanceId; }
    public void setAgentInstanceId(String agentInstanceId) {
        this.agentInstanceId = agentInstanceId;
        if (this.agentId == null) this.agentId = agentInstanceId;
        if (this.sessionId == null) this.sessionId = agentInstanceId;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) {
        this.agentId = agentId;
        if (this.agentInstanceId == null) this.agentInstanceId = agentId;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getSubtaskId() { return subtaskId; }
    public void setSubtaskId(String subtaskId) { this.subtaskId = subtaskId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getAgentDefinitionId() { return agentDefinitionId; }
    public void setAgentDefinitionId(Long agentDefinitionId) { this.agentDefinitionId = agentDefinitionId; }

    public ReActPhaseType getPhase() { return phase; }
    public void setPhase(ReActPhaseType phase) { this.phase = phase; }

    public int getLoopCount() { return loopCount; }
    public void setLoopCount(int loopCount) { this.loopCount = loopCount; }
    public void incrementLoopCount() { this.loopCount++; }

    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }
    public void incrementStepNumber() { this.stepNumber++; }

    public int getMaxSteps() { return maxSteps; }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }

    public int getTokenBudget() { return tokenBudget; }
    public void setTokenBudget(int tokenBudget) { this.tokenBudget = tokenBudget; }

    public int getTokenUsed() { return tokenUsed; }
    public void setTokenUsed(int tokenUsed) { this.tokenUsed = tokenUsed; }
    public void addTokenUsed(int delta) { this.tokenUsed += delta; }

    public long getCostUsedCent() { return costUsedCent; }
    public void setCostUsedCent(long costUsedCent) { this.costUsedCent = costUsedCent; }
    public void addCostCent(long delta) { this.costUsedCent += delta; }

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public void incrementRetryCount() { this.retryCount++; }

    public int getMaxRetry() { return maxRetry; }
    public void setMaxRetry(int maxRetry) { this.maxRetry = maxRetry; }

    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }

    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }

    public Map<String, Object> getMemory() { return memory; }
    public void setMemory(Map<String, Object> memory) { this.memory = memory; }

    /** 生成新的 agent_instance_id（用于测试） */
    public static ReActContext forTest(String taskId, int maxSteps, int tokenBudget) {
        String id = UUID.randomUUID().toString();
        return new ReActContext(id, id, taskId, maxSteps, tokenBudget);
    }
}
