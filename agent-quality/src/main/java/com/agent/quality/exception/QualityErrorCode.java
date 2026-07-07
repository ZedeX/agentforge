package com.agent.quality.exception;

/**
 * Quality 模块错误码（对齐 doc 02-api §0.5 错误码规范）。
 *
 * <p>补充 agent-common ErrorCode 中未覆盖的 quality 专属错误码。
 * 结构与 {@link com.agent.common.exception.ErrorCode} 一致（code / httpStatus / defaultMessage），
 * 供 {@link QualityException} 使用。</p>
 */
public enum QualityErrorCode {

    BADCASE_NOT_FOUND("BADCASE_NOT_FOUND", 404, "Badcase 记录不存在"),
    REVIEW_ITEM_NOT_FOUND("REVIEW_ITEM_NOT_FOUND", 404, "审核条目不存在"),
    VALIDATION_LAYER_INVALID("VALIDATION_LAYER_INVALID", 400, "无效的校验层级"),
    BADCASE_WRITE_FAILED("BADCASE_WRITE_FAILED", 500, "Badcase 写入失败"),
    REVIEW_QUEUE_ERROR("REVIEW_QUEUE_ERROR", 500, "审核队列操作失败");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    QualityErrorCode(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
