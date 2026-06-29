package com.agent.memory.model;

import com.agent.memory.enums.TaskOutcome;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Task execution result (F12.D1 write trigger input).
 */
public class TaskResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskId;
    private TaskOutcome outcome;
    private String goal;
    private List<String> steps = new ArrayList<>();
    private String tenantId;
    private String agentId;

    public TaskResult() {
    }

    public TaskResult(String taskId, TaskOutcome outcome, String goal) {
        this.taskId = taskId;
        this.outcome = outcome;
        this.goal = goal;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public TaskOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(TaskOutcome outcome) {
        this.outcome = outcome;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = steps;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
}
