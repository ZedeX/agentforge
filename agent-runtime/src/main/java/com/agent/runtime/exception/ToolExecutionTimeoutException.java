package com.agent.runtime.exception;

import com.agent.common.exception.ErrorCode;

/**
 * Thrown when tool invocation exceeds deadline (T5).
 *
 * <p>Maps from gRPC {@code Status.Code.DEADLINE_EXCEEDED}.
 * {@link ErrorCode#TOOL_TIMEOUT} (504).
 */
public class ToolExecutionTimeoutException extends ToolEngineException {

    public ToolExecutionTimeoutException(String message, Throwable cause) {
        super(ErrorCode.TOOL_TIMEOUT, message, cause);
    }

    public ToolExecutionTimeoutException(String message) {
        super(ErrorCode.TOOL_TIMEOUT, message);
    }
}
