package com.agent.knowledge.model;

/**
 * Vector search result (doc 07-knowledge §7.1 SearchChunks response item).
 *
 * <p>Holds chunkId + similarity score + content + docId for retrieval response.</p>
 */
public class SearchResult {

    private final String chunkId;
    private final double score;
    private final String content;
    private final String docId;
    private final String kbId;

    public SearchResult(String chunkId, double score, String content, String docId, String kbId) {
        this.chunkId = chunkId;
        this.score = score;
        this.content = content;
        this.docId = docId;
        this.kbId = kbId;
    }

    public String getChunkId() { return chunkId; }

    public double getScore() { return score; }

    public String getContent() { return content; }

    public String getDocId() { return docId; }

    public String getKbId() { return kbId; }
}
