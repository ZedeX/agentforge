package com.agent.tool.engine.exception;

/**
 * Thrown when R3 tool approval is missing or only partially approved (F8 R3 dual approval).
 *
 * <p>errorCode = APPROVAL_REQUIRED, httpStatus = 403.</p>
 */
public class ToolApprovalException extends ToolEngineException {

    public static final String CODE_APPROVAL_REQUIRED = "APPROVAL_REQUIRED";
    public static final String CODE_APPROVAL_EXPIRED = "APPROVAL_EXPIRED";

    public ToolApprovalException(String errorCode, String message) {
        super(errorCode, 403, message);
    }
}
