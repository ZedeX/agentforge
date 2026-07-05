package com.agent.runtime.exception;

import com.agent.common.exception.ErrorCode;

/**
 * Thrown when tool invocation requires approval (T5).
 *
 * <p>Maps from gRPC {@code Status.Code.PERMISSION_DENIED}.
 * {@link ErrorCode#TOOL_RISK_DENIED} (403).
 *
 * <p>Carries optional {@code approvalCallId} for the orchestrator to follow up
 * with the approval flow.
 */
public class ToolApprovalRequiredException extends ToolEngineException {

    private final String approvalCallId;

    public ToolApprovalRequiredException(String message, String approvalCallId, Throwable cause) {
        super(ErrorCode.TOOL_RISK_DENIED, message, cause);
        this.approvalCallId = approvalCallId;
    }

    public ToolApprovalRequiredException(String message, String approvalCallId) {
        super(ErrorCode.TOOL_RISK_DENIED, message);
        this.approvalCallId = approvalCallId;
    }

    public String getApprovalCallId() { return approvalCallId; }
}
