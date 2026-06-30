package com.agent.repo.enums;

/**
 * Agent model tier (doc 01-database §6.1 model_tier column, doc 06-agent-repo §2.2).
 *
 * <p>LITE: 轻量级 Agent, 走 gpt-4o-mini / qwen-turbo 等轻量模型
 * STANDARD: 标准级 Agent, 走 gpt-4o / claude-3.5-sonnet 等通用模型
 * ADVANCED: 高级 Agent, 走 claude-3.5-opus / gpt-4-turbo 等强模型</p>
 */
public enum AgentTier {

    LITE("lite", "轻量级"),
    STANDARD("standard", "标准级"),
    ADVANCED("advanced", "高级");

    private final String code;
    private final String description;

    AgentTier(String code, String description) {
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
     * Parse tier from code string (case-insensitive).
     *
     * @param code tier code (lite / standard / advanced)
     * @return AgentTier enum, default STANDARD if null/unknown
     */
    public static AgentTier fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return STANDARD;
        }
        String lower = code.toLowerCase();
        for (AgentTier t : values()) {
            if (t.code.equals(lower)) {
                return t;
            }
        }
        return STANDARD;
    }
}
