package com.agent.quality.exception;

import com.agent.common.exception.ErrorCode;

/**
 * Quality 持久化异常（HTTP 500，gRPC INTERNAL）。
 *
 * <p>Badcase 写入 / 审核条目持久化失败时抛出。</p>
 */
public class QualityPersistenceException extends QualityException {

    public QualityPersistenceException(String message) {
        super(ErrorCode.INTERNAL, message);
    }

    public QualityPersistenceException(String message, Throwable cause) {
        super(ErrorCode.INTERNAL, message, cause);
    }
}
