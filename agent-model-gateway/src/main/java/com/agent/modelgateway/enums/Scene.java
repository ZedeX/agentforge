package com.agent.modelgateway.enums;

/**
 * Routing scene (doc 02-api §3, PRD §二(二)1 routing strategy).
 *
 * <p>INTENT: 意图识别 / 轻量任务, 路由到轻量模型 (e.g. gpt-4o-mini / qwen-turbo)
 * AUDIT: 强模型终审 / 质量校验, 路由到强模型 (e.g. gpt-4o / claude-3.5-sonnet)
 * GENERIC: 默认场景, 按 weight + cost 路由</p>
 */
public enum Scene {

    INTENT("intent", "意图识别场景, 路由轻量模型"),
    AUDIT("audit", "强模型终审场景, 路由强模型"),
    GENERIC("generic", "默认场景, 按 weight+cost 路由");

    private final String code;
    private final String description;

    Scene(String code, String description) {
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
     * Parse scene from code string (case-insensitive).
     *
     * @param code scene code (intent / audit / generic)
     * @return Scene enum, default GENERIC if null/unknown
     */
    public static Scene fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return GENERIC;
        }
        String lower = code.toLowerCase();
        for (Scene s : values()) {
            if (s.code.equals(lower)) {
                return s;
            }
        }
        return GENERIC;
    }
}
