package com.agent.memory.api;

import com.agent.memory.model.DedupReport;
import com.agent.memory.model.MemoryRecord;

import java.util.List;

/**
 * Memory deduper port (F12.D4: cosine ≥ 0.92 merge, else insert new + Plan 03 T9 batch dedup).
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
     * Batch deduplicate a list of memory records (Plan 03 T9):
     * <ul>
     *   <li>Group by contentHash — same hash → keep oldest, drop rest (dropped)</li>
     *   <li>Cosine ≥ cosineHigh → merge content + keep higher importance (merged)</li>
     *   <li>Cosine cosineLow~cosineHigh → mark related (related)</li>
     *   <li>Below threshold → keep all (kept)</li>
     * </ul>
     *
     * @return dedup report with counts
     */
    DedupReport dedup(List<MemoryRecord> batch);

    /**
     * Threshold above which memories are considered duplicates (default 0.92).
     */
    default double dedupThreshold() {
        return 0.92;
    }
}
