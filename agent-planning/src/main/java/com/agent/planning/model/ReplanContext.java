package com.agent.planning.model;

/**
 * Replan context (doc 03-task-engine §8.2.1 PlanningService.Replan request payload).
 *
 * <p>Carries replan trigger reason + previous DAG + replan count for ReplanStrategy decision.</p>
 */
public class ReplanContext {

    private String taskId;
    private String reason;
    private int replanCount;
    private String previousDagJson;
    private String failedNodeId;
    private String tenantId;
    private String traceId;

    public ReplanContext() {
    }

    public ReplanContext(String taskId, String reason, int replanCount) {
        this.taskId = taskId;
        this.reason = reason;
        this.replanCount = replanCount;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getReplanCount() { return replanCount; }
    public void setReplanCount(int replanCount) { this.replanCount = replanCount; }

    public String getPreviousDagJson() { return previousDagJson; }
    public void setPreviousDagJson(String previousDagJson) { this.previousDagJson = previousDagJson; }

    public String getFailedNodeId() { return failedNodeId; }
    public void setFailedNodeId(String failedNodeId) { this.failedNodeId = failedNodeId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}
