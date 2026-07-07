package com.agent.hallucination.exception;

/**
 * 幻觉治理模块错误码（F10 专用，映射到 agent-common ErrorCode 的补充）。
 *
 * <p>code 前缀 {@code HALL_}，httpStatus 对齐 doc 02-api §0.5。</p>
 */
public enum HallucinationErrorCode {

    SELF_CHECK_FAILED("HALL_SELF_CHECK_FAILED", 500, "自检失败"),
    RAG_ANCHOR_FAILED("HALL_RAG_ANCHOR_FAILED", 500, "RAG 锚定失败"),
    TOOL_GUARD_REJECTED("HALL_TOOL_GUARD_REJECTED", 403, "工具调用被守卫拒绝"),
    HARD_VALIDATION_FAILED("HALL_HARD_VALIDATION_FAILED", 500, "硬校验失败"),
    METRIC_WRITE_FAILED("HALL_METRIC_WRITE_FAILED", 500, "指标写入失败"),
    METRIC_NOT_FOUND("HALL_METRIC_NOT_FOUND", 404, "指标记录不存在"),
    INVALID_CLAIM("HALL_INVALID_CLAIM", 400, "无效 claim"),
    INVALID_GUARD_REQUEST("HALL_INVALID_GUARD_REQUEST", 400, "无效工具守卫请求"),
    INVALID_LAYER("HALL_INVALID_LAYER", 400, "无效幻觉治理层"),
    INVALID_EVENT_TYPE("HALL_INVALID_EVENT_TYPE", 400, "无效事件类型");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    HallucinationErrorCode(String code, int httpStatus, String defaultMessage) {
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
