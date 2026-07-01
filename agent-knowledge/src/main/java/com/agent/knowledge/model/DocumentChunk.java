package com.agent.knowledge.model;

import com.agent.knowledge.enums.IngestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Document chunk (doc 01-database §7 knowledge_chunk table, Plan 08 T8).
 *
 * <p>JPA Entity backing knowledge_chunk table. Stores chunk content, token count,
 * ordering within document, Milvus embedding id and ingestion status.
 * IngestStatus persisted as enum name (STRING).</p>
 */
@Entity
@Table(name = "knowledge_chunk", uniqueConstraints = @UniqueConstraint(name = "uk_chunk_id", columnNames = "chunk_id"))
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chunk_id", nullable = false, length = 32, unique = true)
    private String chunkId;

    @Column(name = "doc_id", nullable = false, length = 32)
    private String docId;

    @Column(name = "kb_id", nullable = false, length = 32)
    private String kbId;

    @Column(name = "content", nullable = false, length = 65535)
    private String content;

    @Column(name = "token_count", nullable = false)
    private int tokenCount = 0;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(name = "embedding_id", length = 128)
    private String embeddingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IngestStatus status = IngestStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private long createdAt;

    public DocumentChunk() {
    }

    public DocumentChunk(String chunkId, String docId, String kbId, String content) {
        this.chunkId = chunkId;
        this.docId = docId;
        this.kbId = kbId;
        this.content = content;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == 0) {
            createdAt = System.currentTimeMillis();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getKbId() { return kbId; }
    public void setKbId(String kbId) { this.kbId = kbId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getTokenCount() { return tokenCount; }
    public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public String getEmbeddingId() { return embeddingId; }
    public void setEmbeddingId(String embeddingId) { this.embeddingId = embeddingId; }

    public IngestStatus getStatus() { return status; }
    public void setStatus(IngestStatus status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
