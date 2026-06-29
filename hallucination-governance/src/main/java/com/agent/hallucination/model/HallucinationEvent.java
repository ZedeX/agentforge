package com.agent.hallucination.model;

import com.agent.hallucination.enums.HallucinationLayer;

import java.io.Serializable;
import java.time.Instant;

/**
 * A detected hallucination event (F10 L6 metric input).
 */
public class HallucinationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private HallucinationLayer detectedLayer;
    private String claimText;
    private double severity;
    private Instant detectedAt;
    private String tenantId;
    private String agentId;

    public HallucinationEvent() {
    }

    public HallucinationEvent(HallucinationLayer detectedLayer, String claimText, double severity) {
        this.detectedLayer = detectedLayer;
        this.claimText = claimText;
        this.severity = severity;
        this.detectedAt = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public HallucinationLayer getDetectedLayer() {
        return detectedLayer;
    }

    public void setDetectedLayer(HallucinationLayer detectedLayer) {
        this.detectedLayer = detectedLayer;
    }

    public String getClaimText() {
        return claimText;
    }

    public void setClaimText(String claimText) {
        this.claimText = claimText;
    }

    public double getSeverity() {
        return severity;
    }

    public void setSeverity(double severity) {
        this.severity = severity;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
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
