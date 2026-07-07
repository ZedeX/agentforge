package com.agent.quality.exception;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;

import java.util.Map;

/**
 * Quality 模块业务异常（继承 {@link BusinessException}）。
 *
 * <p>支持使用 agent-common {@link ErrorCode} 或 quality 专属 {@link QualityErrorCode} 构造。</p>
 */
public class QualityException extends BusinessException {

    private final QualityErrorCode qualityErrorCode;

    public QualityException(ErrorCode errorCode) {
        super(errorCode);
        this.qualityErrorCode = null;
    }

    public QualityException(ErrorCode errorCode, String message) {
        super(errorCode, message);
        this.qualityErrorCode = null;
    }

    public QualityException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(errorCode, message, details);
        this.qualityErrorCode = null;
    }

    public QualityException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.qualityErrorCode = null;
    }

    public QualityException(QualityErrorCode errorCode) {
        super(toCommonErrorCode(errorCode), errorCode.getDefaultMessage());
        this.qualityErrorCode = errorCode;
    }

    public QualityException(QualityErrorCode errorCode, String message) {
        super(toCommonErrorCode(errorCode), message);
        this.qualityErrorCode = errorCode;
    }

    public QualityErrorCode getQualityErrorCode() {
        return qualityErrorCode;
    }

    private static ErrorCode toCommonErrorCode(QualityErrorCode qec) {
        return switch (qec.getHttpStatus()) {
            case 404 -> ErrorCode.VALIDATION_FAILED;
            case 400 -> ErrorCode.PARAM_INVALID;
            default -> ErrorCode.INTERNAL;
        };
    }
}
