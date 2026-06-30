package com.agent.planning.model;

import java.util.List;

/**
 * Plan template (doc 03-task-engine §8.2 PlanMode.TEMPLATE).
 *
 * <p>Pre-defined DAG template for high-frequency scenarios (e.g. weekly report, order query).
 * Matched by scenario tags + success rate filtering.</p>
 */
public class PlanTemplate {

    private Long id;
    private String name;
    private String description;
    private List<String> scenarioTags;
    private String dagJson;
    private double successRate = 0.0;
    private int useCount = 0;
    private boolean enabled = true;

    public PlanTemplate() {
    }

    public PlanTemplate(String name, List<String> scenarioTags, String dagJson) {
        this.name = name;
        this.scenarioTags = scenarioTags;
        this.dagJson = dagJson;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getScenarioTags() { return scenarioTags; }
    public void setScenarioTags(List<String> scenarioTags) { this.scenarioTags = scenarioTags; }

    public String getDagJson() { return dagJson; }
    public void setDagJson(String dagJson) { this.dagJson = dagJson; }

    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }

    public int getUseCount() { return useCount; }
    public void setUseCount(int useCount) { this.useCount = useCount; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
