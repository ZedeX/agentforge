package com.agent.planning.model;

/**
 * Planning context (doc 03-task-engine §8.2.1 PlanningService.Plan request payload).
 *
 * <p>Carries task metadata + user preferences across the planning pipeline
 * (assessComplexity → plan → validatePlan → replan).</p>
 */
public class PlanningContext {

    private String taskId;
    private String goal;
    private String tenantId;
    private String userId;
    private boolean preferTemplate = true;
    private long costBudgetCent;
    private String taskSchemaJson;
    private String traceId;

    public PlanningContext() {
    }

    public PlanningContext(String taskId, String goal, String tenantId) {
        this.taskId = taskId;
        this.goal = goal;
        this.tenantId = tenantId;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public boolean isPreferTemplate() { return preferTemplate; }
    public void setPreferTemplate(boolean preferTemplate) { this.preferTemplate = preferTemplate; }

    public long getCostBudgetCent() { return costBudgetCent; }
    public void setCostBudgetCent(long costBudgetCent) { this.costBudgetCent = costBudgetCent; }

    public String getTaskSchemaJson() { return taskSchemaJson; }
    public void setTaskSchemaJson(String taskSchemaJson) { this.taskSchemaJson = taskSchemaJson; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}
