package com.agent.knowledge.model;

import com.agent.knowledge.enums.KnowledgeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Knowledge base metadata (doc 01-database §7.1 knowledge_base table, Plan 08 T8).
 *
 * <p>JPA Entity backing knowledge_base table. Stores KB identity, embedding model config,
 * doc/chunk counters and lifecycle status. Status persisted as enum name (STRING) so
 * CREATING / READY / UPDATING / ERROR / DELETED remain readable in DB.</p>
 */
@Entity
@Table(name = "knowledge_base", uniqueConstraints = @UniqueConstraint(name = "uk_kb_id", columnNames = "kb_id"))
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kb_id", nullable = false, length = 32, unique = true)
    private String kbId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 65535)
    private String description;

    @Column(name = "doc_count", nullable = false)
    private int docCount = 0;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount = 0;

    @Column(name = "embedding_model", nullable = false, length = 64)
    private String embeddingModel = "bge-large-zh-v1.5";

    @Column(name = "dimension", nullable = false)
    private int dimension = 1024;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private KnowledgeStatus status = KnowledgeStatus.CREATING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public KnowledgeBase() {
    }

    public KnowledgeBase(String kbId, String name) {
        this.kbId = kbId;
        this.name = name;
    }

    @PrePersist
    protected void onCreate() {
        long now = System.currentTimeMillis();
        if (createdAt == 0) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = System.currentTimeMillis();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
