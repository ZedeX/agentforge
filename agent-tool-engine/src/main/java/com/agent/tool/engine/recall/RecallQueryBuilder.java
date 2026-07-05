package com.agent.tool.engine.recall;

import com.agent.tool.engine.model.ToolMeta;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Builds the natural-language query string for memory recall.
 *
 * <p>Combines the tool name, description, and input params into a single
 * query text that the memory service can use for semantic search.</p>
 */
public final class RecallQueryBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RecallQueryBuilder() {
    }

    /**
     * Build a query from tool metadata + params.
     *
     * @param toolId     tool identifier
     * @param toolName   tool display name (may be null)
     * @param description tool description (may be null)
     * @param params     input params map (may be null or empty)
     * @return query string, never null
     */
    public static String build(String toolId, String toolName,
                               String description, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder(128);
        if (toolName != null && !toolName.isBlank()) {
            sb.append(toolName);
        }
        if (description != null && !description.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(description);
        }
        if (toolId != null && !toolId.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(toolId);
        }
        if (params != null && !params.isEmpty()) {
            try {
                String paramsJson = MAPPER.writeValueAsString(params);
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(paramsJson);
            } catch (Exception e) {
                // JSON serialization failure is non-fatal; skip params
            }
        }
        return sb.toString();
    }

    /**
     * Build a query from {@link ToolMeta} + params.
     */
    public static String build(ToolMeta meta, Map<String, Object> params) {
        if (meta == null) {
            return build(null, null, null, params);
        }
        return build(meta.getToolId(), meta.getName(), meta.getDescription(), params);
    }
}
