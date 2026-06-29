package com.agent.tool.engine.enums;

/**
 * Tool call execution status (F8 audit log status field).
 */
public enum ToolCallStatus {
    SUCCESS,
    FAILED,
    TIMEOUT,
    CACHED,
    SKIPPED
}
