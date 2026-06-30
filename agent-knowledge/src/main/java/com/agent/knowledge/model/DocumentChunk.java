package com.agent.knowledge.model;

import com.agent.knowledge.enums.IngestStatus;

/**
 * Document chunk (doc 01-database §7.2 knowledge_chunk table).
 *
 * <p>Skeleton stage: in-memory POJO. JPA Entity deferred to Plan 08 T8.</p>
 */
public class DocumentChunk {

    private String chunkId;
    private String docId;
    private String kbId;
    private String content;
    private int tokenCount;
    private int orderIndex;
    private String embeddingId;
    private IngestStatus status = IngestStatus.PENDING;

    public DocumentChunk() {
    }

    public DocumentChunk(String chunkId, String docId, String kbId, String content) {
        this.chunkId = chunkId;
        this.docId = docId;
        this.kbId = kbId;
        this.content = content;
    }

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
}
