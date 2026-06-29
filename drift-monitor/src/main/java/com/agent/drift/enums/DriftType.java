package com.agent.drift.enums;

/**
 * Drift type classification (doc 11-detail-flow F11).
 */
public enum DriftType {

    /** Tool call rate anomaly: consecutive tool call rate rise >20%. */
    BEHAVIOR_DRIFT,
    /** Task success rate declining: consecutive success rate drop. */
    EFFECT_DRIFT,
    /** Output deviates from task goal (cosine_sim < threshold). */
    ALIGNMENT_DRIFT,
    /** Recall relevance decline: memory drift. */
    MEMORY_DRIFT,
    /** No drift detected. */
    NONE
}
