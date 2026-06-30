package com.agent.planning.enums;

/**
 * Replan mode (doc 03-task-engine §10.1 / ReplanModeSelector).
 *
 * <p>INCREMENTAL: 单点失败, 增量重规划 (只重做失败节点)
 * FULL: 根节点变更或结构错误, 全量重规划
 * MANUAL: 重规划次数超限, 转人工介入</p>
 */
public enum ReplanMode {

    INCREMENTAL("incremental", "增量重规划, 只重做失败节点"),
    FULL("full", "全量重规划, 根节点变更或结构错误"),
    MANUAL("manual", "重规划次数超限, 转人工介入");

    private final String code;
    private final String description;

    ReplanMode(String code, String description) {
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
     * Parse replan mode from code string (case-insensitive).
     *
     * @param code mode code (incremental / full / manual)
     * @return ReplanMode enum, default INCREMENTAL if null/unknown
     */
    public static ReplanMode fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return INCREMENTAL;
        }
        String lower = code.toLowerCase();
        for (ReplanMode m : values()) {
            if (m.code.equals(lower)) {
                return m;
            }
        }
        return INCREMENTAL;
    }
}
