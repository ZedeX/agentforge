package com.agent.repo.enums;

/**
 * Agent lifecycle status (doc 06-agent-repo §2.3, PRD §二(二) agent-repo state machine).
 *
 * <p>DRAFT: 草稿态, 创建后默认状态, 允许自由修改
 * PUBLISHED: 已发布, 锁定不可改, 必须先 rollback 到 DRAFT 才能再改
 * DEPRECATED: 已弃用, 不再推荐使用, 老引用仍可调
 * ARCHIVED: 已归档, 完全下线, 不可再发布</p>
 *
 * <p>State machine (单向流转): DRAFT → PUBLISHED → DEPRECATED → ARCHIVED.</p>
 */
public enum AgentStatus {

    DRAFT("draft", "草稿"),
    PUBLISHED("published", "已发布"),
    DEPRECATED("deprecated", "已弃用"),
    ARCHIVED("archived", "已归档");

    private final String code;
    private final String description;

    AgentStatus(String code, String description) {
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
     * @param code status code (draft / published / deprecated / archived)
     * @return AgentStatus enum, default DRAFT if null/unknown
     */
    public static AgentStatus fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return DRAFT;
        }
        String lower = code.toLowerCase();
        for (AgentStatus s : values()) {
            if (s.code.equals(lower)) {
                return s;
            }
        }
        return DRAFT;
    }

    /**
     * Check whether transition from this status to target is allowed.
     *
     * <p>Allowed: DRAFT→PUBLISHED, PUBLISHED→DEPRECATED, DEPRECATED→ARCHIVED,
     * DRAFT→DRAFT (no-op). All others rejected.</p>
     *
     * @param target target status
     * @return true if transition is legal
     */
    public boolean canTransitionTo(AgentStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        switch (this) {
            case DRAFT:
                return target == PUBLISHED;
            case PUBLISHED:
                return target == DEPRECATED;
            case DEPRECATED:
                return target == ARCHIVED;
            case ARCHIVED:
                return false;
            default:
                return false;
        }
    }
}
