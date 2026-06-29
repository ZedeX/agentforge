package com.agent.tool.engine.api;

import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;

/**
 * Tool gateway port (F8 invoke entry: approval check -> sandbox/proxy exec -> result clean).
 */
public interface ToolGateway {

    /**
     * Invoke a tool after approval + sandbox checks.
     *
     * @throws com.agent.tool.engine.exception.ToolApprovalException      when R3 approval missing or expired
     * @throws com.agent.tool.engine.exception.ToolValidationException   when params schema invalid
     * @throws com.agent.tool.engine.exception.ToolQuotaExhaustedException when tenant quota exhausted
     */
    ToolCallResult invoke(ToolCallRequest request);
}
