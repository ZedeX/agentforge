package com.agent.tool.engine.api;

import com.agent.tool.engine.model.ToolRecallResult;

import java.util.List;
import java.util.Map;

/**
 * Tool semantic recaller port (F8.D1/D2 recall + rerank).
 *
 * <p>T10: evolved to call agent-memory's Recall RPC for historical tool-call
 * experience, with keyword fallback when the memory service is unavailable.</p>
 */
public interface ToolSemanticRecaller {

    /**
     * Recall top-K results by semantic similarity.
     *
     * <p>Calls agent-memory's Recall RPC with a query built from toolId +
     * params. If the memory service is unavailable or times out, falls back
     * to keyword matching against the tool registry.</p>
     *
     * @param tenantId tenant scope
     * @param toolId   target tool ID (may be null for broad recall)
     * @param params   input params map (used to build query text)
     * @param topK     max results
     * @return recalled results sorted by score desc; empty list when no match
     */
    List<ToolRecallResult> recall(String tenantId, String toolId,
                                   Map<String, Object> params, int topK);

    /**
     * Convenience: recall with default topK=3.
     */
    default List<ToolRecallResult> recallDefault3(String tenantId, String toolId,
                                                   Map<String, Object> params) {
        return recall(tenantId, toolId, params, 3);
    }

    /**
     * Legacy recall by query string (T1-T8 backward compatibility).
     *
     * @deprecated use {@link #recall(String, String, Map, int)} instead.
     */
    @Deprecated
    List<ToolRecallResult> recall(String query, int topK);
}
