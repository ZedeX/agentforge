package com.agent.knowledge.enums;

/**
 * Chunk splitting strategy (doc 07-knowledge §4.1).
 *
 * <p>TOKEN: 按 token 上限切分 (默认 maxTokens=512, overlap=64)
 * PARAGRAPH: 按段落优先切分 (段落超长时回退到 token 切分)
 * FIXED: 按固定字符长度切分 (用于测试 / 简单场景)</p>
 */
public enum ChunkStrategyType {

    TOKEN("token", "Token 上限切分"),
    PARAGRAPH("paragraph", "段落优先切分"),
    FIXED("fixed", "固定长度切分");

    private final String code;
    private final String description;

    ChunkStrategyType(String code, String description) {
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
     * Parse strategy from code string (case-insensitive).
     *
     * @param code strategy code (token / paragraph / fixed)
     * @return ChunkStrategyType enum, default TOKEN if null/empty, default TOKEN if unrecognized
     */
    public static ChunkStrategyType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return TOKEN;
        }
        String lower = code.toLowerCase();
        for (ChunkStrategyType s : values()) {
            if (s.code.equals(lower)) {
                return s;
            }
        }
        return TOKEN;
    }
}
