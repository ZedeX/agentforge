package com.agent.tool.engine.model;

import java.io.Serializable;

/**
 * Semantic recall result POJO (F8.D1/D2 recall + rerank).
 *
 * <p>T10 extension: carries memory content + source metadata from the
 * agent-memory Recall RPC, in addition to the original toolId/name/score
 * fields.</p>
 */
public class ToolRecallResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String toolId;
    private String name;
    private double score;

    // ---- T10 memory recall extension fields ----

    /** Memory content text (the recalled experience / procedure). */
    private String content;

    /** Source task ID from the memory record. */
    private String sourceTaskId;

    /** Importance score [0.0, 1.0] from the memory record. */
    private double importance;

    public ToolRecallResult() {
    }

    /** Legacy 3-arg constructor (backward compatible with T1-T8 callers). */
    public ToolRecallResult(String toolId, String name, double score) {
        this.toolId = toolId;
        this.name = name;
        this.score = score;
    }

    /** T10 full-arg constructor. */
    public ToolRecallResult(String toolId, String name, double score,
                            String content, String sourceTaskId, double importance) {
        this.toolId = toolId;
        this.name = name;
        this.score = score;
        this.content = content;
        this.sourceTaskId = sourceTaskId;
        this.importance = importance;
    }

    public String getToolId() { return toolId; }
    public void setToolId(String toolId) { this.toolId = toolId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(String sourceTaskId) { this.sourceTaskId = sourceTaskId; }

    public double getImportance() { return importance; }
    public void setImportance(double importance) { this.importance = importance; }

    @Override
    public String toString() {
        return "ToolRecallResult{toolId='" + toolId + '\'' +
                ", name='" + name + '\'' +
                ", score=" + score +
                ", importance=" + importance +
                ", sourceTaskId='" + sourceTaskId + '\'' + '}';
    }
}
