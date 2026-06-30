package com.agent.repo.enums;

/**
 * Agent capability tag (doc 06-agent-repo §3.1 ability_tags classification).
 *
 * <p>Classifies what an Agent can do, used by CapabilityRegistry for indexing
 * and AgentQueryService for filtering.</p>
 *
 * <ul>
 *   <li>CODE_GENERATION: 代码生成 / 补全 / 重构</li>
 *   <li>CODE_REVIEW: 代码审查 / 质量检测 / 安全扫描</li>
 *   <li>TRANSLATION: 翻译 / 本地化 / 多语言转换</li>
 *   <li>QA: 问答 / 客服 / 知识检索</li>
 *   <li>REASONING: 推理 / 决策 / 规划</li>
 *   <li>DATA_ANALYSIS: 数据分析 / 报表 / 可视化</li>
 * </ul>
 */
public enum CapabilityTag {

    CODE_GENERATION("code_generation", "代码生成"),
    CODE_REVIEW("code_review", "代码审查"),
    TRANSLATION("translation", "翻译"),
    QA("qa", "问答"),
    REASONING("reasoning", "推理"),
    DATA_ANALYSIS("data_analysis", "数据分析");

    private final String code;
    private final String description;

    CapabilityTag(String code, String description) {
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
     * Parse tag from code string (case-insensitive).
     *
     * @param code tag code (code_generation / code_review / ...)
     * @return CapabilityTag enum, default QA if null/unknown
     */
    public static CapabilityTag fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return QA;
        }
        String lower = code.toLowerCase();
        for (CapabilityTag t : values()) {
            if (t.code.equals(lower)) {
                return t;
            }
        }
        return QA;
    }
}
