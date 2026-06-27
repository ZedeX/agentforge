package com.agent.common.exception;

/**
 * 平台统一错误码（对应 doc 02-api §0.5 错误码规范）
 */
public enum ErrorCode {

    // 成功
    OK("OK", 200, "成功"),

    // 401 未认证
    UNAUTHENTICATED("UNAUTHENTICATED", 401, "未认证"),

    // 403 无权限
    FORBIDDEN("FORBIDDEN", 403, "无权限"),
    TOOL_RISK_DENIED("TOOL_RISK_DENIED", 403, "工具风险被拒绝"),

    // 404 资源不存在
    TASK_NOT_FOUND("TASK_NOT_FOUND", 404, "任务不存在"),
    AGENT_NOT_FOUND("AGENT_NOT_FOUND", 404, "Agent 不存在"),
    TOOL_NOT_FOUND("TOOL_NOT_FOUND", 404, "工具不存在"),

    // 400 参数校验
    VALIDATION_FAILED("VALIDATION_FAILED", 400, "参数校验失败"),
    PARAM_INVALID("PARAM_INVALID", 400, "参数非法"),
    CONTENT_BLOCKED("CONTENT_BLOCKED", 400, "内容被拦截"),

    // 409 状态冲突
    TASK_STATUS_CONFLICT("TASK_STATUS_CONFLICT", 409, "任务状态冲突"),
    DAG_CYCLE_DETECTED("DAG_CYCLE_DETECTED", 409, "DAG 检测到环"),
    DAG_VERSION_CONFLICT("DAG_VERSION_CONFLICT", 409, "DAG 版本冲突"),

    // 429 限流
    RATE_LIMITED("RATE_LIMITED", 429, "限流"),
    QUOTA_EXCEEDED("QUOTA_EXCEEDED", 429, "配额超限"),
    COST_BUDGET_EXCEEDED("COST_BUDGET_EXCEEDED", 429, "成本预算超限"),

    // 500 内部错误
    INTERNAL("INTERNAL", 500, "内部错误"),
    MODEL_GATEWAY_ERROR("MODEL_GATEWAY_ERROR", 500, "模型网关错误"),
    COMPLETENESS_FAIL("COMPLETENESS_FAIL", 500, "完整性校验失败"),
    REPLAN_EXHAUSTED("REPLAN_EXHAUSTED", 500, "重规划次数耗尽"),
    HALLUCINATION_SUSPECTED("HALLUCINATION_SUSPECTED", 500, "疑似幻觉"),
    FACT_INCONSISTENCY("FACT_INCONSISTENCY", 500, "事实不一致"),
    MAX_STEPS_EXCEEDED("MAX_STEPS_EXCEEDED", 500, "超过最大步数"),
    CONTEXT_WINDOW_EXHAUSTED("CONTEXT_WINDOW_EXHAUSTED", 500, "上下文窗口耗尽"),

    // 503 服务不可用
    DEPENDENCY_DOWN("DEPENDENCY_DOWN", 503, "依赖服务不可用"),
    CIRCUIT_OPEN("CIRCUIT_OPEN", 503, "熔断开启"),

    // 504 超时
    TIMEOUT("TIMEOUT", 504, "超时"),
    TOOL_TIMEOUT("TOOL_TIMEOUT", 504, "工具调用超时"),
    MODEL_TIMEOUT("MODEL_TIMEOUT", 504, "模型调用超时");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, int httpStatus, String defaultMessage) {
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
