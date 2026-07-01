package com.agent.knowledge.api;

import com.agent.knowledge.enums.ChunkStrategyType;
import com.agent.knowledge.enums.DocumentType;
import com.agent.knowledge.model.IngestResult;

/**
 * Document ingestor (doc 07-knowledge §4 + §5, Plan 08 T9).
 *
 * <p>Orchestrates document parsing → chunk splitting → JPA persistence.
 * Idempotent: re-ingesting the same kbId+docId replaces old chunks with new ones.</p>
 */
public interface DocumentIngestor {

    /**
     * Ingest a document into a knowledge base.
     *
     * <p>Flow: validate KB → idempotent delete old chunks → parse → split →
     * persist KnowledgeDocument + DocumentChunk[] → update KB stats.</p>
     *
     * @param kbId     knowledge base id, must exist and not be DELETED
     * @param docId    document id, null/empty for auto-generate
     * @param name     document display name
     * @param content  raw document content
     * @param type     document type, null defaults to TEXT
     * @param strategy chunk strategy, null defaults to TOKEN
     * @param maxTokens max tokens per chunk (strategy=TOKEN/PARAGRAPH), <=0 defaults to 512
     * @param overlap   overlap tokens (strategy=TOKEN), <0 defaults to 0
     * @return IngestResult with chunkIds + stats; success=false if KB missing or content empty
     */
    IngestResult ingestDocument(String kbId, String docId, String name, String content,
                                DocumentType type, ChunkStrategyType strategy,
                                int maxTokens, int overlap);
}
