package com.agent.hallucination.model;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Hallucination rate metric row (agent_metrics_daily) for F10 L6 tracking.
 */
public class HallucinationMetric implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String agentId;
    private LocalDate statDate;
    private double hallucinationRate;
    private long totalClaims;
    private long hallucinationCount;

    public HallucinationMetric() {
    }

    public HallucinationMetric(String tenantId, String agentId, double hallucinationRate) {
        this.tenantId = tenantId;
        this.agentId = agentId;
        this.hallucinationRate = hallucinationRate;
        this.statDate = LocalDate.now();
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

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public double getHallucinationRate() {
        return hallucinationRate;
    }

    public void setHallucinationRate(double hallucinationRate) {
        this.hallucinationRate = hallucinationRate;
    }

    public long getTotalClaims() {
        return totalClaims;
    }

    public void setTotalClaims(long totalClaims) {
        this.totalClaims = totalClaims;
    }

    public long getHallucinationCount() {
        return hallucinationCount;
    }

    public void setHallucinationCount(long hallucinationCount) {
        this.hallucinationCount = hallucinationCount;
    }
}
