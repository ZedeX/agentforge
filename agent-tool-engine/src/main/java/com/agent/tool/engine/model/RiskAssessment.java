package com.agent.tool.engine.model;

import com.agent.tool.engine.enums.ToolRiskLevel;

import java.io.Serializable;
import java.util.Objects;

/**
 * Risk assessment result produced by {@code RiskClassifier}.
 *
 * <p>Carries the resolved {@link ToolRiskLevel}, whether human approval is
 * required before execution, and a human-readable reason explaining the
 * classification decision (for audit logging / prompt injection).</p>
 */
public class RiskAssessment implements Serializable {

    private static final long serialVersionUID = 1L;

    private ToolRiskLevel riskLevel;
    private boolean requiresApproval;
    private String reason;

    public RiskAssessment() {
    }

    public RiskAssessment(ToolRiskLevel riskLevel, boolean requiresApproval, String reason) {
        this.riskLevel = riskLevel;
        this.requiresApproval = requiresApproval;
        this.reason = reason;
    }

    public ToolRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(ToolRiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RiskAssessment that)) return false;
        return requiresApproval == that.requiresApproval
                && riskLevel == that.riskLevel
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(riskLevel, requiresApproval, reason);
    }

    @Override
    public String toString() {
        return "RiskAssessment{riskLevel=" + riskLevel
                + ", requiresApproval=" + requiresApproval
                + ", reason='" + reason + "'}";
    }
}
