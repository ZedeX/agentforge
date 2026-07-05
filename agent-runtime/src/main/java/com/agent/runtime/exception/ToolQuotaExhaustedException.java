package com.agent.runtime.exception;

import com.agent.common.exception.ErrorCode;

/**
 * Thrown when tool invocation quota is exhausted (T5).
 *
 * <p>Maps from gRPC {@code Status.Code.RESOURCE_EXHAUSTED}.
 * {@link ErrorCode#QUOTA_EXCEEDED} (429).
 */
public class ToolQuotaExhaustedException extends ToolEngineException {

    public ToolQuotaExhaustedException(String message, Throwable cause) {
        super(ErrorCode.QUOTA_EXCEEDED, message, cause);
    }

    public ToolQuotaExhaustedException(String message) {
        super(ErrorCode.QUOTA_EXCEEDED, message);
    }
}
