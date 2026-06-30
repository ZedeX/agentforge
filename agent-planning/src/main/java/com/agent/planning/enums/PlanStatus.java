package com.agent.planning.enums;

/**
 * Plan lifecycle status (doc 03-task-engine §6.2 plan state subset).
 *
 * <p>DRAFT: 草稿, 规划中
 * VALIDATED: 通过 5 维度自检
 * EXECUTING: 已分发到 DAG 引擎执行
 * COMPLETED: 全部步骤完成
 * FAILED: 校验失败或执行失败
 * REPLANNED: 触发重规划, 已废弃当前版本</p>
 */
public enum PlanStatus {

    DRAFT("draft", "草稿, 规划中"),
    VALIDATED("validated", "通过 5 维度自检"),
    EXECUTING("executing", "已分发到 DAG 引擎执行"),
    COMPLETED("completed", "全部步骤完成"),
    FAILED("failed", "校验失败或执行失败"),
    REPLANNED("replanned", "触发重规划, 已废弃当前版本");

    private final String code;
    private final String description;

    PlanStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse status from code string (case-insensitive).
     *
     * @param code status code (draft / validated / executing / ...)
     * @return PlanStatus enum, default DRAFT if null/unknown
     */
    public static PlanStatus fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return DRAFT;
        }
        String lower = code.toLowerCase();
        for (PlanStatus s : values()) {
            if (s.code.equals(lower)) {
                return s;
            }
        }
        return DRAFT;
    }
}
