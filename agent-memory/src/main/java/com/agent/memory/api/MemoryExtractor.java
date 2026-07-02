package com.agent.memory.api;

import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.ExtractedMemory;
import com.agent.memory.model.TaskResult;

import java.util.List;

/**
 * Memory extractor port (F12.D2: episodic / semantic / procedural / reflective extraction).
 *
 * <p>Two extraction modes:
 * <ul>
 *   <li>{@link #extract} — explicit type, single memory</li>
 *   <li>{@link #extractFromTaskResult} — auto-determine type by outcome, may produce multiple</li>
 * </ul>
 */
public interface MemoryExtractor {

    /**
     * Extract memory of given type from task result.
     */
    ExtractedMemory extract(TaskResult taskResult, MemoryType type);

    /**
     * Auto-determine memory type(s) by task outcome and extract (Plan 03 T3):
     * <ul>
     *   <li>SUCCESS → PROCEDURAL（步骤序列 + 关键决策）</li>
     *   <li>FAILURE → REFLECTIVE（失败原因 + 反思）</li>
     *   <li>PARTIAL → PROCEDURAL + REFLECTIVE（两类都产）</li>
     *   <li>TIMEOUT → REFLECTIVE（超时反思）</li>
     * </ul>
     *
     * @return extracted memories (may be empty if content filtered or null input)
     */
    List<ExtractedMemory> extractFromTaskResult(TaskResult taskResult);
}
