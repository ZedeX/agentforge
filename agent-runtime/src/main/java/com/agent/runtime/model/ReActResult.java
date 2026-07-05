package com.agent.runtime.model;

import com.agent.runtime.enums.ReflexionResult;

/**
 * ReAct 循环执行结果（doc 06-runtime §9.1，T3）。
 */
public class ReActResult {

    private String agentInstanceId;
    private String sessionId;
    private String finalAnswer;
    private int stepCount;
    private int totalTokensUsed;
    private long totalCostCent;
    /** 终止状态：SUCCESS / FAILED / ABORTED / REPLAN_REQUESTED / CANCELLED */
    private String status;
    /** 触发 REPLAN 时的原因（仅 status=REPLAN_REQUESTED 时有值） */
    private String replanReason;
    private ReflexionResult finalReflexion;

    public ReActResult() {
    }

    public static ReActResult success(String agentInstanceId, String sessionId, String finalAnswer,
                                       int stepCount, int totalTokensUsed, long totalCostCent) {
        ReActResult r = new ReActResult();
        r.agentInstanceId = agentInstanceId;
        r.sessionId = sessionId;
        r.finalAnswer = finalAnswer;
        r.stepCount = stepCount;
        r.totalTokensUsed = totalTokensUsed;
        r.totalCostCent = totalCostCent;
        r.status = "SUCCESS";
        r.finalReflexion = ReflexionResult.CONTINUE;
        return r;
    }

    public static ReActResult aborted(String agentInstanceId, String sessionId, String reason,
                                       int stepCount, int totalTokensUsed) {
        ReActResult r = new ReActResult();
        r.agentInstanceId = agentInstanceId;
        r.sessionId = sessionId;
        r.finalAnswer = null;
        r.stepCount = stepCount;
        r.totalTokensUsed = totalTokensUsed;
        r.status = "ABORTED";
        r.finalReflexion = ReflexionResult.ABORT;
        r.replanReason = reason;
        return r;
    }

    public static ReActResult replanRequested(String agentInstanceId, String sessionId, String reason,
                                               int stepCount, int totalTokensUsed) {
        ReActResult r = new ReActResult();
        r.agentInstanceId = agentInstanceId;
        r.sessionId = sessionId;
        r.finalAnswer = null;
        r.stepCount = stepCount;
        r.totalTokensUsed = totalTokensUsed;
        r.status = "REPLAN_REQUESTED";
        r.finalReflexion = ReflexionResult.REPLAN;
        r.replanReason = reason;
        return r;
    }

    public static ReActResult cancelled(String agentInstanceId, String sessionId,
                                         int stepCount, int totalTokensUsed) {
        ReActResult r = new ReActResult();
        r.agentInstanceId = agentInstanceId;
        r.sessionId = sessionId;
        r.finalAnswer = null;
        r.stepCount = stepCount;
        r.totalTokensUsed = totalTokensUsed;
        r.status = "CANCELLED";
        return r;
    }

    public String getAgentInstanceId() { return agentInstanceId; }
    public void setAgentInstanceId(String agentInstanceId) { this.agentInstanceId = agentInstanceId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }

    public int getStepCount() { return stepCount; }
    public void setStepCount(int stepCount) { this.stepCount = stepCount; }

    public int getTotalTokensUsed() { return totalTokensUsed; }
    public void setTotalTokensUsed(int totalTokensUsed) { this.totalTokensUsed = totalTokensUsed; }

    public long getTotalCostCent() { return totalCostCent; }
    public void setTotalCostCent(long totalCostCent) { this.totalCostCent = totalCostCent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReplanReason() { return replanReason; }
    public void setReplanReason(String replanReason) { this.replanReason = replanReason; }

    public ReflexionResult getFinalReflexion() { return finalReflexion; }
    public void setFinalReflexion(ReflexionResult finalReflexion) { this.finalReflexion = finalReflexion; }

    public boolean isFinished() {
        return "SUCCESS".equals(status) || "ABORTED".equals(status)
                || "REPLAN_REQUESTED".equals(status) || "CANCELLED".equals(status);
    }
}
