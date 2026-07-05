package com.agent.runtime.exception;

import com.agent.common.exception.ErrorCode;

/**
 * Thrown when model gateway call exceeds deadline (T4).
 *
 * <p>Maps from gRPC {@code Status.Code.DEADLINE_EXCEEDED}.
 * {@link ErrorCode#MODEL_TIMEOUT} (504).
 */
public class ModelGatewayTimeoutException extends ModelGatewayException {

    public ModelGatewayTimeoutException(String message, Throwable cause) {
        super(ErrorCode.MODEL_TIMEOUT, message, cause);
    }

    public ModelGatewayTimeoutException(String message) {
        super(ErrorCode.MODEL_TIMEOUT, message);
    }
}
