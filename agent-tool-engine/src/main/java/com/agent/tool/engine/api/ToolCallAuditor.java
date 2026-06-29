package com.agent.tool.engine.api;

import com.agent.tool.engine.model.ToolCallAuditLog;

/**
 * Tool call auditor port (F8 audit: tool_call_log persistence).
 */
public interface ToolCallAuditor {

    /**
     * Persist audit log entry for a tool call (success or failure).
     */
    void audit(ToolCallAuditLog log);
}
