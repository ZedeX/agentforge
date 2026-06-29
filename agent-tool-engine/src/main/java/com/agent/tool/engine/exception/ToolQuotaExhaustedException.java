package com.agent.tool.engine.exception;

/**
 * Thrown when tenant tool quota is exhausted (F8 quota branch).
 *
 * <p>errorCode = RATE_LIMITED, httpStatus = 429.</p>
 */
public class ToolQuotaExhaustedException extends ToolEngineException {

    public static final String CODE_RATE_LIMITED = "RATE_LIMITED";

    public ToolQuotaExhaustedException(String message) {
        super(CODE_RATE_LIMITED, 429, message);
    }
}
