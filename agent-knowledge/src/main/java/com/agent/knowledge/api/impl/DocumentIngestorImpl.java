package com.agent.knowledge.api.impl;

import com.agent.knowledge.api.ChunkSplitter;
import com.agent.knowledge.api.DocumentIngestor;
import com.agent.knowledge.api.DocumentParser;
import com.agent.knowledge.enums.ChunkStrategyType;
import com.agent.knowledge.enums.DocumentType;
import com.agent.knowledge.enums.IngestStatus;
import com.agent.knowledge.enums.KnowledgeStatus;
import com.agent.knowledge.model.DocumentChunk;
import com.agent.knowledge.model.IngestResult;
import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.model.KnowledgeDocument;
import com.agent.knowledge.repository.DocumentChunkRepository;
import com.agent.knowledge.repository.KnowledgeBaseRepository;
import com.agent.knowledge.repository.KnowledgeDocumentRepository;
import com.agent.knowledge.util.TokenCounter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Document ingestor service (doc 07-knowledge §4 + §5, Plan 08 T9).
 *
 * <p>Orchestrates document parsing → chunk splitting → JPA persistence.
 * Idempotent: re-ingesting the same kbId+docId replaces old chunks with new ones.
 * KB stats (docCount / chunkCount) recomputed from DB after each ingest for accuracy.</p>
 *
 * <p>Depends on Wave 24 T8 JPA repositories (DocumentChunk / KnowledgeBase / KnowledgeDocument)
 * and skeleton-stage ChunkSplitter + DocumentParser. TokenCounter (T9) used for token counting.</p>
 */
@Component
public class DocumentIngestorImpl implements DocumentIngestor {

    private final DocumentParser documentParser;
    private final ChunkSplitter chunkSplitter;
    private final DocumentChunkRepository chunkRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeDocumentRepository documentRepository;

    public DocumentIngestorImpl(DocumentParser documentParser,
                                ChunkSplitter chunkSplitter,
                                DocumentChunkRepository chunkRepository,
                                KnowledgeBaseRepository kbRepository,
                                KnowledgeDocumentRepository documentRepository) {
        this.documentParser = documentParser;
        this.chunkSplitter = chunkSplitter;
        this.chunkRepository = chunkRepository;
        this.kbRepository = kbRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    @Transactional
    public IngestResult ingestDocument(String kbId, String docId, String name, String content,
                                       DocumentType type, ChunkStrategyType strategy,
                                       int maxTokens, int overlap) {
        // 1. Validate KB existence
        Optional<KnowledgeBase> kbOpt = kbRepository.findByKbId(kbId);
        if (kbOpt.isEmpty()) {
            return new IngestResult(docId, kbId, List.of(), false,
                    "KB not found: " + kbId, 0, 0);
        }
        KnowledgeBase kb = kbOpt.get();
        if (kb.getStatus() == KnowledgeStatus.DELETED) {
            return new IngestResult(docId, kbId, List.of(), false,
                    "KB is deleted: " + kbId, 0, 0);
        }

        // 2. Validate content
        if (content == null || content.isEmpty()) {
            return new IngestResult(docId, kbId, List.of(), false,
                    "Content is empty", 0, 0);
        }

        // 3. Auto-generate docId if missing
        if (docId == null || docId.isEmpty()) {
            docId = "doc-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (name == null || name.isEmpty()) {
            name = docId;
        }

        // 4. Idempotent: delete old chunks + old document for same kbId+docId
        chunkRepository.deleteByKbIdAndDocId(kbId, docId);
        documentRepository.findByDocId(docId).ifPresent(documentRepository::delete);
        // flush to ensure deletes take effect before re-querying stats
        documentRepository.flush();
        chunkRepository.flush();

        // 5. Parse document → plain text
        DocumentType docType = type != null ? type : DocumentType.TEXT;
        String plainText = documentParser.parse(content, docType);
        if (plainText == null || plainText.isEmpty()) {
            return new IngestResult(docId, kbId, List.of(), false,
                    "Parsed content is empty", 0, 0);
        }

        // 6. Split into chunks
        ChunkStrategyType strat = strategy != null ? strategy : ChunkStrategyType.TOKEN;
        List<String> chunkTexts = chunkSplitter.split(plainText, strat, maxTokens, overlap);
        if (chunkTexts.isEmpty()) {
            return new IngestResult(docId, kbId, List.of(), false,
                    "No chunks produced", 0, 0);
        }

        // 7. Persist KnowledgeDocument
        int totalTokens = 0;
        for (String ct : chunkTexts) {
            totalTokens += TokenCounter.count(ct);
        }
        KnowledgeDocument document = new KnowledgeDocument(docId, kbId, name);
        document.setType(docType);
        document.setContent(content);
        document.setSizeBytes(content.getBytes().length);
        document.setChunkCount(chunkTexts.size());
        document.setTokenCount(totalTokens);
        documentRepository.save(document);

        // 8. Persist DocumentChunk[]
        List<String> chunkIds = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            String chunkText = chunkTexts.get(i);
            String chunkId = "chunk-" + UUID.randomUUID().toString().substring(0, 8);
            DocumentChunk chunk = new DocumentChunk(chunkId, docId, kbId, chunkText);
            chunk.setOrderIndex(i);
            chunk.setTokenCount(TokenCounter.count(chunkText));
            chunk.setStatus(IngestStatus.PENDING);
            chunkRepository.save(chunk);
            chunkIds.add(chunkId);
        }

        // 9. Update KB stats (recompute from DB for accuracy)
        long totalChunksInKb = chunkRepository.countByKbId(kbId);
        long totalDocsInKb = documentRepository.findByKbId(kbId).size();
        kb.setChunkCount((int) totalChunksInKb);
        kb.setDocCount((int) totalDocsInKb);
        if (kb.getStatus() == KnowledgeStatus.CREATING) {
            kb.setStatus(KnowledgeStatus.UPDATING);
        }
        kbRepository.save(kb);

        return new IngestResult(docId, kbId, chunkIds, true,
                "Ingested " + chunkIds.size() + " chunks", chunkIds.size(), totalTokens);
    }
}
