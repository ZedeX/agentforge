package com.agent.tool.engine.gateway;

import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.exception.ToolEngineException;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.model.ToolMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MCP (Model Context Protocol) executor placeholder (doc 05-tool-engine §3.1).
 *
 * <p>Full MCP protocol integration is deferred to T12. This placeholder:
 * <ul>
 *   <li>Logs a warning on each call.</li>
 *   <li>Returns a FAILED result with a clear "MCP not yet implemented" error stack
 *       (rather than throwing) so the gateway can audit + clean the failure
 *       uniformly.</li>
 * </ul>
 * </p>
 *
 * <p>When T12 lands, replace {@link #execute} with a real MCP client that
 * speaks the MCP JSON-RPC protocol over stdio/SSE to the delegatee process.</p>
 */
@Component
public class McpExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(McpExecutor.class);

    @Override
    public ExecutorType type() {
        return ExecutorType.MCP;
    }

    @Override
    public ToolCallResult execute(ToolMeta meta, ToolCallRequest request, long timeoutMs) {
        log.warn("MCP executor not yet implemented (T12): tool={}, request={}",
                meta.getToolId(), request.getTraceId());

        ToolCallResult result = new ToolCallResult(
                meta.getToolId(), "", ToolCallStatus.FAILED);
        result.setErrorStack("MCP protocol executor not yet implemented (deferred to T12)");
        return result;
    }
}
