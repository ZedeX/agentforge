package com.agent.hallucination.enums;

/**
 * Hallucination governance six-layer architecture (doc 11-detail-flow F10).
 */
public enum HallucinationLayer {

    /** Layer 1: high-risk scene route to strong model with low temperature. */
    L1_ROUTE,
    /** Layer 2: step-level self-check on each claim. */
    L2_SELF_CHECK,
    /** Layer 3: RAG factual anchor + source tag + cross verify. */
    L3_RAG_ANCHOR,
    /** Layer 4: L4-1 hard validation fallback. */
    L4_HARD,
    /** Layer 5: tool gateway param guard. */
    L5_TOOL_GUARD,
    /** Layer 6: hallucination rate metric tracking. */
    L6_METRIC
}
