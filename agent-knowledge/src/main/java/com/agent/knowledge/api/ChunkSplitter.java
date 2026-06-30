package com.agent.knowledge.api;

import com.agent.knowledge.enums.ChunkStrategyType;

import java.util.List;

/**
 * Chunk splitter (doc 07-knowledge §4.1, PRD §二(二) chunking).
 *
 * <p>Splits text into chunks by strategy (token / paragraph / fixed length).
 * Skeleton stage: heuristic token counter + paragraph splitting.
 * Full TokenChunkStrategy deferred to Plan 08 T9.</p>
 */
public interface ChunkSplitter {

    /**
     * Split content into chunks.
     *
     * @param content   text to split
     * @param strategy  splitting strategy, null defaults to TOKEN
     * @param maxTokens max tokens per chunk (token strategy) or max chars (fixed strategy)
     * @param overlap   overlap tokens between adjacent chunks
     * @return list of chunk texts, empty if content is null/empty
     */
    List<String> split(String content, ChunkStrategyType strategy, int maxTokens, int overlap);
}
