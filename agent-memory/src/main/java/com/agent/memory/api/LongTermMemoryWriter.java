package com.agent.memory.api;

import com.agent.memory.model.MemoryRecord;

/**
 * Long-term memory writer port (F12.D1: skip write on task failure, write on success).
 */
public interface LongTermMemoryWriter {

    /**
     * Write a long-term memory record.
     *
     * @return memoryId assigned by store; null when write skipped.
     */
    String write(MemoryRecord record);

    /**
     * Skip write (e.g. task failed, low importance).
     *
     * @param reason skip reason
     */
    void skipWrite(String reason);
}
