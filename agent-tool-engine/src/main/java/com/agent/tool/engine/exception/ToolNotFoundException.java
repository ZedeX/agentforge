package com.agent.tool.engine.exception;

/**
 * Thrown when a toolId is not found in the registry (T12 gRPC mapping).
 *
 * <p>errorCode = TOOL_NOT_FOUND, httpStatus = 404. Maps to gRPC
 * {@code Status.NOT_FOUND}.</p>
 */
public class ToolNotFoundException extends ToolEngineException {

    public static final String CODE_TOOL_NOT_FOUND = "TOOL_NOT_FOUND";

    public ToolNotFoundException(String message) {
        super(CODE_TOOL_NOT_FOUND, 404, message);
    }

    public ToolNotFoundException(String errorCode, int httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }
}
