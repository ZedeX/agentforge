package com.agent.runtime.api;

import com.agent.runtime.api.dto.ToolInvokeRequest;
import com.agent.runtime.api.dto.ToolInvokeResult;

import java.util.concurrent.CompletableFuture;

/**
 * Tool engine client (F6 act phase: execute tool call).
 *
 * <p>Routes to agent-tool-engine module ({@code ToolGateway.Invoke} RPC).
 *
 * <p>Two interfaces:
 * <ul>
 *   <li>{@link #invoke(ToolInvokeRequest)} — synchronous unary call (T5 primary contract).</li>
 *   <li>{@link #invokeAsync(ToolInvokeRequest)} — async call returning {@link CompletableFuture}.</li>
 *   <li>{@link #invoke(String, String)} — legacy simplified interface (kept for ActPhase mock path).</li>
 * </ul>
 *
 * <p>Error mapping (T5 §12.4):
 * <ul>
 *   <li>NOT_FOUND → {@link com.agent.runtime.exception.ToolNotFoundException}</li>
 *   <li>PERMISSION_DENIED → {@link com.agent.runtime.exception.ToolApprovalRequiredException}</li>
 *   <li>RESOURCE_EXHAUSTED → {@link com.agent.runtime.exception.ToolQuotaExhaustedException}</li>
 *   <li>DEADLINE_EXCEEDED → {@link com.agent.runtime.exception.ToolExecutionTimeoutException}</li>
 *   <li>others → {@link com.agent.runtime.exception.ToolEngineException}</li>
 * </ul>
 */
public interface ToolEngineClient {

    /**
     * Synchronous tool invocation. Sends {@code request} to tool gateway {@code Invoke} RPC.
     *
     * @param request structured invoke request (callId/toolId/inputJson/agentId/...)
     * @return parsed invoke result (status/outputJson/durationMs/...)
     */
    ToolInvokeResult invoke(ToolInvokeRequest request);

    /**
     * Asynchronous tool invocation returning a {@link CompletableFuture}.
     *
     * <p>Useful for ActPhase non-blocking execution; errors complete the future exceptionally.
     *
     * @param request structured invoke request
     * @return future that completes with invoke result or throws {@link com.agent.runtime.exception.ToolEngineException}
     */
    CompletableFuture<ToolInvokeResult> invokeAsync(ToolInvokeRequest request);

    /**
     * Legacy simplified invoke: toolId + args JSON → result JSON string.
     *
     * <p>Kept for ActPhase mock path and tests; production code should prefer {@link #invoke(ToolInvokeRequest)}.
     *
     * @param toolId tool identifier
     * @param args tool input arguments (JSON)
     * @return tool execution result (JSON)
     * @deprecated use {@link #invoke(ToolInvokeRequest)} for structured I/O and error mapping
     */
    @Deprecated
    String invoke(String toolId, String args);
}
