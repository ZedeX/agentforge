package com.agent.modelgateway.model;

import com.agent.modelgateway.enums.Scene;

/**
 * Route rule (doc 01-database §5.2 model_route_rule table).
 *
 * <p>Matches by scene + priority, selecting primary + fallback providers.</p>
 */
public class ModelRouteRule {

    private Long id;
    private Scene scene;
    private int priority;
    private String fromProviderCode;
    private String primaryProviderCode;
    private String fallbackProviderCode;
    private double costCeilingUsd = 0.0;
    private boolean enabled = true;

    public ModelRouteRule() {
    }

    public ModelRouteRule(Scene scene, int priority, String primaryProviderCode, String fallbackProviderCode) {
        this.scene = scene;
        this.priority = priority;
        this.primaryProviderCode = primaryProviderCode;
        this.fallbackProviderCode = fallbackProviderCode;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Scene getScene() { return scene; }
    public void setScene(Scene scene) { this.scene = scene; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getFromProviderCode() { return fromProviderCode; }
    public void setFromProviderCode(String fromProviderCode) { this.fromProviderCode = fromProviderCode; }

    public String getPrimaryProviderCode() { return primaryProviderCode; }
    public void setPrimaryProviderCode(String primaryProviderCode) { this.primaryProviderCode = primaryProviderCode; }

    public String getFallbackProviderCode() { return fallbackProviderCode; }
    public void setFallbackProviderCode(String fallbackProviderCode) { this.fallbackProviderCode = fallbackProviderCode; }

    public double getCostCeilingUsd() { return costCeilingUsd; }
    public void setCostCeilingUsd(double costCeilingUsd) { this.costCeilingUsd = costCeilingUsd; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
