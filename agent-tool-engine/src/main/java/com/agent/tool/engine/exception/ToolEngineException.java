package com.agent.tool.engine.exception;

/**
 * Base exception for tool engine errors (F8 invoke failures).
 *
 * <p>Carries an errorCode string aligned with doc 02-api §0.5 error codes.</p>
 */
public class ToolEngineException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public ToolEngineException(String errorCode, int httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
