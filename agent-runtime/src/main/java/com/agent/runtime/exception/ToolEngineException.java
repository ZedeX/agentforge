package com.agent.runtime.exception;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;

/**
 * Base exception for tool engine client errors (T5, doc 06-runtime §12.4).
 *
 * <p>All tool engine related runtime exceptions extend this class.
 * Subclasses select the appropriate {@link ErrorCode}:
 * <ul>
 *   <li>{@link ToolNotFoundException} → {@link ErrorCode#TOOL_NOT_FOUND} (404)</li>
 *   <li>{@link ToolApprovalRequiredException} → {@link ErrorCode#TOOL_RISK_DENIED} (403)</li>
 *   <li>{@link ToolQuotaExhaustedException} → {@link ErrorCode#QUOTA_EXCEEDED} (429)</li>
 *   <li>{@link ToolExecutionTimeoutException} → {@link ErrorCode#TOOL_TIMEOUT} (504)</li>
 * </ul>
 */
public class ToolEngineException extends BusinessException {

    public ToolEngineException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ToolEngineException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
