package com.agent.drift.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Behavior baseline anchored on first run (F11 L1: BaselineAnchor).
 */
public class BehaviorBaseline implements Serializable {

    private static final long serialVersionUID = 1L;

    private String agentId;
    private String version;
    private String goldenSetHash;
    private Instant anchoredAt;
    private double baselineToolCallRate;
    private double baselineSuccessRate;

    public BehaviorBaseline() {
    }

    public BehaviorBaseline(String agentId, String version, String goldenSetHash) {
        this.agentId = agentId;
        this.version = version;
        this.goldenSetHash = goldenSetHash;
        this.anchoredAt = Instant.now();
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGoldenSetHash() {
        return goldenSetHash;
    }

    public void setGoldenSetHash(String goldenSetHash) {
        this.goldenSetHash = goldenSetHash;
    }

    public Instant getAnchoredAt() {
        return anchoredAt;
    }

    public void setAnchoredAt(Instant anchoredAt) {
        this.anchoredAt = anchoredAt;
    }

    public double getBaselineToolCallRate() {
        return baselineToolCallRate;
    }

    public void setBaselineToolCallRate(double baselineToolCallRate) {
        this.baselineToolCallRate = baselineToolCallRate;
    }

    public double getBaselineSuccessRate() {
        return baselineSuccessRate;
    }

    public void setBaselineSuccessRate(double baselineSuccessRate) {
        this.baselineSuccessRate = baselineSuccessRate;
    }
}
