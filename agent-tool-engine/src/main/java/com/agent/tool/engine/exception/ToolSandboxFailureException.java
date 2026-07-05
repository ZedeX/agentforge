package com.agent.tool.engine.exception;

/**
 * Thrown when the Docker sandbox fails to borrow/create/start a container
 * or the pool is exhausted (doc 05-tool-engine §12.4).
 *
 * <p>errorCode = TOOL_SANDBOX_FAILURE, httpStatus = 500.</p>
 */
public class ToolSandboxFailureException extends ToolEngineException {

    public static final String CODE_SANDBOX_FAILURE = "TOOL_SANDBOX_FAILURE";
    public static final String CODE_POOL_EXHAUSTED = "TOOL_SANDBOX_POOL_EXHAUSTED";

    public ToolSandboxFailureException(String message) {
        super(CODE_SANDBOX_FAILURE, 500, message);
    }

    public ToolSandboxFailureException(String errorCode, String message) {
        super(errorCode, 500, message);
    }

    public ToolSandboxFailureException(String message, Throwable cause) {
        super(CODE_SANDBOX_FAILURE, 500, message);
        initCause(cause);
    }
}
