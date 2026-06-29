package com.agent.tool.engine.api;

import com.agent.tool.engine.model.ToolRecallResult;

import java.util.List;

/**
 * Tool semantic recaller port (F8.D1/D2 recall + rerank).
 */
public interface ToolSemanticRecaller {

    /**
     * Recall top-K tools by semantic similarity to the query.
     *
     * @param query natural language query
     * @param topK  max results
     * @return recalled tools sorted by score desc; empty list when no tool matches threshold
     */
    List<ToolRecallResult> recall(String query, int topK);
}
