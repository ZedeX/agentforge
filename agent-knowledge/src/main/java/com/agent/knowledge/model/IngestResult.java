package com.agent.knowledge.model;

import java.util.List;

/**
 * Document ingestion result (doc 07-knowledge §5 IngestDocument response).
 *
 * <p>Returned by DocumentIngestor after splitting + (skeleton) embedding orchestration.</p>
 */
public class IngestResult {

    private final String docId;
    private final String kbId;
    private final List<String> chunkIds;
    private final boolean success;
    private final String message;
    private final int chunkCount;
    private final int tokenCount;

    public IngestResult(String docId, String kbId, List<String> chunkIds,
                        boolean success, String message, int chunkCount, int tokenCount) {
        this.docId = docId;
        this.kbId = kbId;
        this.chunkIds = chunkIds;
        this.success = success;
        this.message = message;
        this.chunkCount = chunkCount;
        this.tokenCount = tokenCount;
    }

    public String getDocId() { return docId; }

    public String getKbId() { return kbId; }

    public List<String> getChunkIds() { return chunkIds; }

    public boolean isSuccess() { return success; }

    public String getMessage() { return message; }

    public int getChunkCount() { return chunkCount; }

    public int getTokenCount() { return tokenCount; }
}
