package com.agent.tool.engine.exception;

/**
 * Thrown when tool params fail JSON schema validation (F8.D3 false branch).
 *
 * <p>errorCode = VALIDATION_FAILED, httpStatus = 400.</p>
 */
public class ToolValidationException extends ToolEngineException {

    public static final String CODE_VALIDATION_FAILED = "VALIDATION_FAILED";

    public ToolValidationException(String message) {
        super(CODE_VALIDATION_FAILED, 400, message);
    }
}
