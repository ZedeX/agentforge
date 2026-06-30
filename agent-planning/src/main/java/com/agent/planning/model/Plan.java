package com.agent.planning.model;

import com.agent.planning.enums.PlanComplexity;
import com.agent.planning.enums.PlanStatus;

/**
 * Plan entity (doc 03-task-engine §8.2.1 PlanningService.Plan).
 *
 * <p>Skeleton stage: in-memory POJO. JPA Entity annotation deferred to Plan 04 deepening.</p>
 */
public class Plan {

    private Long id;
    private String planId;
    private String taskId;
    private PlanStatus status = PlanStatus.DRAFT;
    private PlanComplexity complexity = PlanComplexity.L1;
    private String dagJson;
    private String source;          // template / ai
    private Long templateId;
    private int version = 1;
    private int replanCount = 0;
    private String tenantId;
    private String userId;
    private long createdAt;
    private long updatedAt;

    public Plan() {
    }

    public Plan(String planId, String taskId) {
        this.planId = planId;
        this.taskId = taskId;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }

    public PlanComplexity getComplexity() { return complexity; }
    public void setComplexity(PlanComplexity complexity) { this.complexity = complexity; }

    public String getDagJson() { return dagJson; }
    public void setDagJson(String dagJson) { this.dagJson = dagJson; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public int getReplanCount() { return replanCount; }
    public void setReplanCount(int replanCount) { this.replanCount = replanCount; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
