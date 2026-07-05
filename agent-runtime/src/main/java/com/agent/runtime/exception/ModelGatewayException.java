package com.agent.runtime.exception;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;

/**
 * Base exception for model gateway client errors (T4, doc 06-runtime §12.4).
 *
 * <p>All model gateway related runtime exceptions extend this class.
 * Subclasses select the appropriate {@link ErrorCode}:
 * <ul>
 *   <li>{@link ModelGatewayUnavailableException} → {@link ErrorCode#MODEL_GATEWAY_ERROR} (500)</li>
 *   <li>{@link ModelGatewayTimeoutException} → {@link ErrorCode#MODEL_TIMEOUT} (504)</li>
 * </ul>
 */
public class ModelGatewayException extends BusinessException {

    public ModelGatewayException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ModelGatewayException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
