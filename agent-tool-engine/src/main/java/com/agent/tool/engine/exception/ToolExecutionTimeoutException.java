package com.agent.tool.engine.exception;

/**
 * Thrown when a sandboxed tool execution exceeds its timeout
 * (doc 05-tool-engine §12.4, execTimeoutMs).
 *
 * <p>errorCode = TOOL_EXECUTION_TIMEOUT, httpStatus = 504.</p>
 */
public class ToolExecutionTimeoutException extends ToolEngineException {

    public static final String CODE_EXEC_TIMEOUT = "TOOL_EXECUTION_TIMEOUT";

    public ToolExecutionTimeoutException(String message) {
        super(CODE_EXEC_TIMEOUT, 504, message);
    }

    public ToolExecutionTimeoutException(String message, Throwable cause) {
        super(CODE_EXEC_TIMEOUT, 504, message);
        initCause(cause);
    }
}
