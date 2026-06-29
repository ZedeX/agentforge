package com.agent.memory.api;

import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.ExtractedMemory;
import com.agent.memory.model.TaskResult;

/**
 * Memory extractor port (F12.D2: episodic / semantic / procedural extraction).
 */
public interface MemoryExtractor {

    /**
     * Extract memory of given type from task result.
     */
    ExtractedMemory extract(TaskResult taskResult, MemoryType type);
}
