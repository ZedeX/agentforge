package com.agent.runtime.exception;

import com.agent.common.exception.ErrorCode;

/**
 * Thrown when model gateway returns UNAVAILABLE or other non-timeout gRPC errors (T4).
 *
 * <p>Maps from gRPC {@code Status.Code.UNAVAILABLE} / {@code INTERNAL} / {@code UNKNOWN}.
 * {@link ErrorCode#MODEL_GATEWAY_ERROR} (500).
 */
public class ModelGatewayUnavailableException extends ModelGatewayException {

    public ModelGatewayUnavailableException(String message, Throwable cause) {
        super(ErrorCode.MODEL_GATEWAY_ERROR, message, cause);
    }

    public ModelGatewayUnavailableException(String message) {
        super(ErrorCode.MODEL_GATEWAY_ERROR, message);
    }
}
