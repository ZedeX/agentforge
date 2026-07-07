package com.agent.quality.exception;

import com.agent.common.exception.ErrorCode;

/**
 * Quality 校验异常（HTTP 400，gRPC INVALID_ARGUMENT / FAILED_PRECONDITION）。
 *
 * <p>L4 三级校验不通过时抛出。</p>
 */
public class QualityValidationException extends QualityException {

    public QualityValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }

    public QualityValidationException(String message, Throwable cause) {
        super(ErrorCode.VALIDATION_FAILED, message, cause);
    }
}
