package com.agent.tool.engine.model;

import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.SideEffect;
import com.agent.tool.engine.enums.ToolRiskLevel;

import java.io.Serializable;
import java.util.Objects;

/**
 * Tool metadata POJO (doc 02-api §3.1 tool schema three-layer definition).
 *
 * <p>Carries the tool's declared {@link ToolRiskLevel} (may be overridden by
 * the risk classifier following the never-downgrade rule, doc 05 §4.2).</p>
 */
public class ToolMeta implements Serializable {

    private static final long serialVersionUID = 1L;

    private String toolId;
    private String name;
    private String description;
    private ExecutorType executorType;
    private SideEffect sideEffect;
    /** Declared risk level; null lets classifier compute from sideEffect. */
    private ToolRiskLevel riskLevel;
    private String tenantId;
    private int quotaLimit;

    public ToolMeta() {
    }

    public ToolMeta(String toolId, String name, ExecutorType executorType, SideEffect sideEffect) {
        this.toolId = toolId;
        this.name = name;
        this.executorType = executorType;
        this.sideEffect = sideEffect;
    }

    public ToolMeta(String toolId, String name, ExecutorType executorType,
                    SideEffect sideEffect, ToolRiskLevel riskLevel) {
        this.toolId = toolId;
        this.name = name;
        this.executorType = executorType;
        this.sideEffect = sideEffect;
        this.riskLevel = riskLevel;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ExecutorType getExecutorType() {
        return executorType;
    }

    public void setExecutorType(ExecutorType executorType) {
        this.executorType = executorType;
    }

    public SideEffect getSideEffect() {
        return sideEffect;
    }

    public void setSideEffect(SideEffect sideEffect) {
        this.sideEffect = sideEffect;
    }

    public ToolRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(ToolRiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getQuotaLimit() {
        return quotaLimit;
    }

    public void setQuotaLimit(int quotaLimit) {
        this.quotaLimit = quotaLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ToolMeta)) return false;
        ToolMeta toolMeta = (ToolMeta) o;
        return Objects.equals(toolId, toolMeta.toolId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolId);
    }
}
