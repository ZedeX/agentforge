package com.agent.runtime.exception;

import com.agent.common.exception.ErrorCode;

/**
 * Thrown when tool is not found in tool registry (T5).
 *
 * <p>Maps from gRPC {@code Status.Code.NOT_FOUND}.
 * {@link ErrorCode#TOOL_NOT_FOUND} (404).
 */
public class ToolNotFoundException extends ToolEngineException {

    public ToolNotFoundException(String message, Throwable cause) {
        super(ErrorCode.TOOL_NOT_FOUND, message, cause);
    }

    public ToolNotFoundException(String message) {
        super(ErrorCode.TOOL_NOT_FOUND, message);
    }
}
