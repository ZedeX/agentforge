package com.agent.quality.exception;

/**
 * Thrown when badcase persistence fails (e.g. DB write error / queue push failure).
 * Ref: doc 11-detail-flow F9 MAX_RETRY_EXCEEDED 后写入 badcase 表失败分支.
 */
public class BadcasePersistenceException extends RuntimeException {

    public BadcasePersistenceException(String message) {
        super(message);
    }
}
