package com.agent.memory.api;

/**
 * Importance scorer port (F12.D3: freq x recency x relevance).
 */
public interface ImportanceScorer {

    /**
     * Compute importance score for a memory record.
     *
     * @return score in [0.0, 1.0]
     */
    double score(int accessCount, double recency, double relevance);
}
