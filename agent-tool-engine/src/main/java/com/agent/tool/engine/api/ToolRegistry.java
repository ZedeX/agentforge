package com.agent.tool.engine.api;

import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.model.ToolSchema;

/**
 * Tool registry port (doc 02-api §3.1, F8.D1 tool discovery).
 *
 * <p>Responsible for tool registration (three-layer schema) and lookup.</p>
 */
public interface ToolRegistry {

    /**
     * Register a new tool with input + output schema.
     *
     * @param meta         tool metadata
     * @param inputSchema  input JSON schema (required fields etc.)
     * @param outputSchema output JSON schema
     * @return toolId assigned by registry
     */
    String register(ToolMeta meta, ToolSchema inputSchema, ToolSchema outputSchema);

    /**
     * Find input schema by toolId.
     */
    ToolSchema findInputSchema(String toolId);

    /**
     * Find tool metadata by toolId.
     */
    ToolMeta findMeta(String toolId);
}
