package com.agent.drift.model;

import com.agent.drift.enums.DriftType;

import java.io.Serializable;

/**
 * Drift signal observed during monitoring (F11 L2: DriftDetector input).
 */
public class DriftSignal implements Serializable {

    private static final long serialVersionUID = 1L;

    private DriftType type;
    private double score;
    private double threshold;
    private String agentId;
    /** Cosine similarity between output and goal (for ALIGNMENT_DRIFT). */
    private double alignmentCosine;
    /** Recall relevance decline ratio (for MEMORY_DRIFT). */
    private double relevanceDeclineRatio;

    public DriftSignal() {
    }

    public DriftSignal(DriftType type, double score, double threshold) {
        this.type = type;
        this.score = score;
        this.threshold = threshold;
    }

    public DriftType getType() {
        return type;
    }

    public void setType(DriftType type) {
        this.type = type;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public double getAlignmentCosine() {
        return alignmentCosine;
    }

    public void setAlignmentCosine(double alignmentCosine) {
        this.alignmentCosine = alignmentCosine;
    }

    public double getRelevanceDeclineRatio() {
        return relevanceDeclineRatio;
    }

    public void setRelevanceDeclineRatio(double relevanceDeclineRatio) {
        this.relevanceDeclineRatio = relevanceDeclineRatio;
    }
}
