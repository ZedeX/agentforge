package com.agent.knowledge.model;

import com.agent.knowledge.enums.KnowledgeStatus;

/**
 * Knowledge base metadata (doc 01-database §7.1 knowledge_base table).
 *
 * <p>Skeleton stage: in-memory POJO. JPA Entity annotation deferred to Plan 08 T8 deepening.</p>
 */
public class KnowledgeBase {

    private String kbId;
    private String name;
    private String description;
    private int docCount = 0;
    private int chunkCount = 0;
    private String embeddingModel = "bge-large-zh-v1.5";
    private int dimension = 1024;
    private KnowledgeStatus status = KnowledgeStatus.CREATING;
    private long createdAt;
    private long updatedAt;

    public KnowledgeBase() {
    }

    public KnowledgeBase(String kbId, String name) {
        this.kbId = kbId;
        this.name = name;
    }

    public String getKbId() { return kbId; }
    public void setKbId(String kbId) { this.kbId = kbId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getDocCount() { return docCount; }
    public void setDocCount(int docCount) { this.docCount = docCount; }

    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }

    public KnowledgeStatus getStatus() { return status; }
    public void setStatus(KnowledgeStatus status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
