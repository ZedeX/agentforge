package com.agent.tool.engine.gateway;

import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.model.ToolMeta;

/**
 * Tool executor strategy (doc 05-tool-engine §3.1).
 *
 * <p>Each implementation handles one {@link ExecutorType}:
 * <ul>
 *   <li>{@link HttpExecutor} → HTTP_API</li>
 *   <li>{@link ShellExecutor} → SHELL</li>
 *   <li>{@link PythonExecutor} → PYTHON</li>
 *   <li>{@link McpExecutor} → MCP</li>
 * </ul>
 * </p>
 *
 * <p>The gateway dispatches by {@link #type()} lookup.</p>
 */
public interface ToolExecutor {

    /** The {@link ExecutorType} this executor handles. */
    ExecutorType type();

    /**
     * Execute the tool synchronously.
     *
     * @param meta      tool metadata (endpoint / executorType / timeoutMs)
     * @param request   call request (inputJson / params / tenantId)
     * @param timeoutMs max execution time in millis (0 → no timeout)
     * @return execution result (status SUCCESS / FAILED / TIMEOUT)
     * @throws com.agent.tool.engine.exception.ToolEngineException on executor-level failures
     */
    ToolCallResult execute(ToolMeta meta, ToolCallRequest request, long timeoutMs);
}
