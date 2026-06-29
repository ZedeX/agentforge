package com.agent.memory.api;

import com.agent.memory.model.MemoryRecord;

import java.util.Optional;

/**
 * Memory deduper port (F12.D4: cosine >= 0.92 merge, else insert new).
 */
public interface MemoryDeduper {

    /**
     * Find similarity score between new content and existing memories.
     *
     * @return highest cosine similarity in [0.0, 1.0]; 0.0 when no existing memory.
     */
    double findMaxSimilarity(MemoryRecord record);

    /**
     * Merge a new memory into an existing one (update instead of insert).
     *
     * @return merged memory record.
     */
    MemoryRecord merge(MemoryRecord existing, MemoryRecord incoming);

    /**
     * Threshold above which memories are considered duplicates (default 0.92).
     */
    default double dedupThreshold() {
        return 0.92;
    }
}
