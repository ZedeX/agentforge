package com.agent.knowledge.model;

import com.agent.knowledge.enums.DocumentType;
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
 * Ingested document metadata (doc 01-database §7 knowledge_document table, Plan 08 T8).
 *
 * <p>JPA Entity backing knowledge_document table. Stores document identity, type,
 * raw content and chunking counters. DocumentType persisted as enum name (STRING).</p>
 */
@Entity
@Table(name = "knowledge_document", uniqueConstraints = @UniqueConstraint(name = "uk_doc_id", columnNames = "doc_id"))
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id", nullable = false, length = 32, unique = true)
    private String docId;

    @Column(name = "kb_id", nullable = false, length = 32)
    private String kbId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private DocumentType type = DocumentType.TEXT;

    @Column(name = "content", length = 65535)
    private String content;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes = 0;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount = 0;

    @Column(name = "token_count", nullable = false)
    private int tokenCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private long createdAt;

    public KnowledgeDocument() {
    }

    public KnowledgeDocument(String docId, String kbId, String name) {
        this.docId = docId;
        this.kbId = kbId;
        this.name = name;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == 0) {
            createdAt = System.currentTimeMillis();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getKbId() { return kbId; }
    public void setKbId(String kbId) { this.kbId = kbId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public DocumentType getType() { return type; }
    public void setType(DocumentType type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(int sizeBytes) { this.sizeBytes = sizeBytes; }

    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }

    public int getTokenCount() { return tokenCount; }
    public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
