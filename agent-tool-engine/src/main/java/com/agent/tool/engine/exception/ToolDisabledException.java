package com.agent.tool.engine.exception;

/**
 * Thrown when invoking a tool whose {@code enabled=false} flag is set
 * (doc 05-tool-engine §12.4).
 *
 * <p>errorCode = TOOL_DISABLED, httpStatus = 403.</p>
 */
public class ToolDisabledException extends ToolEngineException {

    public static final String CODE_TOOL_DISABLED = "TOOL_DISABLED";

    public ToolDisabledException(String message) {
        super(CODE_TOOL_DISABLED, 403, message);
    }
}
