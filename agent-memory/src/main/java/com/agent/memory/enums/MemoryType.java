package com.agent.memory.enums;

/**
 * Long-term memory type (doc 02 §2, F12.D2 extraction branch).
 */
public enum MemoryType {

    /** Episodic: step sequence with timestamps. */
    EPISODIC,
    /** Semantic: factual knowledge with source. */
    SEMANTIC,
    /** Procedural: operation pattern / template. */
    PROCEDURAL
}
