package com.agent.hallucination.api;

/**
 * Layer 3 RAG anchor port (F10 L3: factual constraint via knowledge recall).
 */
public interface RagAnchor {

    /**
     * Anchor a factual task by forcing RAG recall.
     *
     * @param factualTask task description
     * @return true when sufficient info recalled; false when info insufficient (agent should refuse).
     */
    boolean anchor(String factualTask);
}
