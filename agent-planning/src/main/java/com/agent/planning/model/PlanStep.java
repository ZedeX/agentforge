package com.agent.planning.model;

/**
 * Plan step (doc 03-task-engine §8.2.1 PlanStep).
 *
 * <p>Represents a single node in the plan DAG. Skeleton stage: in-memory POJO.</p>
 */
public class PlanStep {

    private Long id;
    private String stepId;
    private String planId;
    private String nodeId;
    private String title;
    private Long agentId;
    private String status;          // pending / running / success / failed
    private int order;
    private String inputsJson;
    private String outputsJson;
    private int retryCount = 0;

    public PlanStep() {
    }

    public PlanStep(String stepId, String planId, String title) {
        this.stepId = stepId;
        this.planId = planId;
        this.title = title;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    public String getInputsJson() { return inputsJson; }
    public void setInputsJson(String inputsJson) { this.inputsJson = inputsJson; }

    public String getOutputsJson() { return outputsJson; }
    public void setOutputsJson(String outputsJson) { this.outputsJson = outputsJson; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
}
