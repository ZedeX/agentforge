package com.agent.knowledge.model;

import com.agent.knowledge.enums.DocumentType;

/**
 * Ingested document metadata (doc 01-database §7 knowledge_chunk parent doc).
 *
 * <p>Skeleton stage: in-memory POJO. JPA Entity deferred to Plan 08 T8.</p>
 */
public class KnowledgeDocument {

    private String docId;
    private String kbId;
    private String name;
    private DocumentType type = DocumentType.TEXT;
    private String content;
    private int sizeBytes;
    private int chunkCount = 0;
    private int tokenCount = 0;
    private long createdAt;

    public KnowledgeDocument() {
    }

    public KnowledgeDocument(String docId, String kbId, String name) {
        this.docId = docId;
        this.kbId = kbId;
        this.name = name;
    }

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
