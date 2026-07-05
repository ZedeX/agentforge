package com.agent.tool.engine.recall;

import java.io.Serializable;

/**
 * DTO for a memory record recalled from agent-memory's Recall RPC.
 *
 * <p>Decouples the tool-engine from the gRPC-generated {@code RecalledMemory}
 * proto message so that tests can construct instances without proto
 * dependencies. {@link MemoryServiceClientImpl} converts proto messages to
 * this DTO at the adapter boundary.</p>
 */
public class RecalledMemoryDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String memoryId;
    private String content;
    /** task | user | system (PROCEDURAL memories have source_type = "task"). */
    private String sourceType;
    private String sourceTaskId;
    /** [0.0, 1.0] — memories below 0.4 are filtered out by the recaller. */
    private double importanceScore;
    /** [0.0, 1.0] — semantic relevance score from the memory service. */
    private double relevanceScore;
    private long createdAtEpoch;

    public RecalledMemoryDto() {
    }

    public RecalledMemoryDto(String memoryId, String content, String sourceType,
                             String sourceTaskId, double importanceScore,
                             double relevanceScore, long createdAtEpoch) {
        this.memoryId = memoryId;
        this.content = content;
        this.sourceType = sourceType;
        this.sourceTaskId = sourceTaskId;
        this.importanceScore = importanceScore;
        this.relevanceScore = relevanceScore;
        this.createdAtEpoch = createdAtEpoch;
    }

    public String getMemoryId() { return memoryId; }
    public void setMemoryId(String memoryId) { this.memoryId = memoryId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(String sourceTaskId) { this.sourceTaskId = sourceTaskId; }

    public double getImportanceScore() { return importanceScore; }
    public void setImportanceScore(double importanceScore) { this.importanceScore = importanceScore; }

    public double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }

    public long getCreatedAtEpoch() { return createdAtEpoch; }
    public void setCreatedAtEpoch(long createdAtEpoch) { this.createdAtEpoch = createdAtEpoch; }

    @Override
    public String toString() {
        return "RecalledMemoryDto{memoryId='" + memoryId + '\'' +
                ", sourceTaskId='" + sourceTaskId + '\'' +
                ", importance=" + importanceScore +
                ", relevance=" + relevanceScore + '}';
    }
}
