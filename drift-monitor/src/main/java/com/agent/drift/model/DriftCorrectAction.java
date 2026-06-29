package com.agent.drift.model;

import com.agent.drift.enums.DriftLevel;

import java.io.Serializable;

/**
 * Drift correction action (F11 L3: DriftCorrector input).
 */
public class DriftCorrectAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private DriftLevel level;
    private String coreConstraintSummary;
    private String targetVersion;
    private String agentId;

    public DriftCorrectAction() {
    }

    public DriftCorrectAction(DriftLevel level, String agentId) {
        this.level = level;
        this.agentId = agentId;
    }

    public DriftLevel getLevel() {
        return level;
    }

    public void setLevel(DriftLevel level) {
        this.level = level;
    }

    public String getCoreConstraintSummary() {
        return coreConstraintSummary;
    }

    public void setCoreConstraintSummary(String coreConstraintSummary) {
        this.coreConstraintSummary = coreConstraintSummary;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
}
