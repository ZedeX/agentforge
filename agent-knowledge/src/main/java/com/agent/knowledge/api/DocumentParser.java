package com.agent.knowledge.api;

import com.agent.knowledge.enums.DocumentType;

/**
 * Document parser (doc 07-knowledge §4 DocumentIngestor preprocessing).
 *
 * <p>Extracts plain text from various document formats (markdown / html / pdf).
 * Skeleton stage: regex-based stripping. Tika / PDFBox deferred to Plan 08 T9.</p>
 */
public interface DocumentParser {

    /**
     * Parse document content into plain text.
     *
     * @param content raw document content
     * @param type    document type, null defaults to TEXT
     * @return extracted plain text
     */
    String parse(String content, DocumentType type);

    /**
     * Detect document type from content heuristics.
     *
     * @param content raw content
     * @return detected DocumentType, UNKNOWN if cannot determine
     */
    DocumentType detectType(String content);
}
