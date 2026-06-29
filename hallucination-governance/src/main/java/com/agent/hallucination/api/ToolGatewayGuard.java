package com.agent.hallucination.api;

import com.agent.hallucination.enums.GuardResult;
import com.agent.hallucination.model.ToolCallGuardRequest;

/**
 * Layer 5 tool gateway guard port (F10 L5: tool param schema pre-check).
 */
public interface ToolGatewayGuard {

    /**
     * Guard a tool call by validating params against schema.
     *
     * @return ALLOWED when params valid; REJECTED when schema mismatch.
     */
    GuardResult guard(ToolCallGuardRequest request);
}
