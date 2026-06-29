package com.agent.tool.engine.model;

import java.io.Serializable;

/**
 * Semantic recall result POJO (F8.D1/D2 recall + rerank).
 */
public class ToolRecallResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String toolId;
    private String name;
    private double score;

    public ToolRecallResult() {
    }

    public ToolRecallResult(String toolId, String name, double score) {
        this.toolId = toolId;
        this.name = name;
        this.score = score;
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

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
