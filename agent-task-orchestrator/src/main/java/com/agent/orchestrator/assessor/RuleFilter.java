package com.agent.orchestrator.assessor;

import java.util.List;

/**
 * 规则初筛器（doc 03-task-engine §2.3 Stage 1）。
 *
 * <p>基于 goal 长度 / 关键词做快速初筛，返回候选等级 + 置信度。
 * 当置信度 &lt; {@link #CONFIDENCE_THRESHOLD} 时，应调用 ModelAssessor 模型精判
 * （UT-PLAN-005/006 覆盖该判定逻辑，ModelAssessor 接口由后续任务实现）。</p>
 *
 * <p>规则集（来自设计文档 §2.3）：</p>
 * <ol>
 *   <li><b>关键词规则</b>（明确匹配，置信度 = {@link #KEYWORD_MATCH_CONFIDENCE} ≥ 阈值）：
 *     <ul>
 *       <li>L3: "编排" / "协同" / "跨系统" → L3 候选</li>
 *       <li>L2: "分析" / "对比" / "生成报告" → L2 候选</li>
 *       <li>L1: "查询" / "翻译" / "总结" → L1 候选</li>
 *     </ul>
 *   </li>
 *   <li><b>长度规则</b>（模糊匹配，置信度 = {@link #LENGTH_RULE_CONFIDENCE} &lt; 阈值）：
 *     goal 字符数 &lt; {@link #LENGTH_THRESHOLD} → L1 候选</li>
 *   <li><b>无匹配</b>：候选为 null，置信度 = {@link #NO_MATCH_CONFIDENCE} &lt; 阈值</li>
 * </ol>
 *
 * <p>优先级：L3 &gt; L2 &gt; L1 &gt; 长度规则（高复杂度关键词优先）。</p>
 *
 * <p>置信度阈值 = {@value #CONFIDENCE_THRESHOLD}（对齐 doc 03-task-engine §11
 * task.planning.ruleFilterConfidenceThreshold）。</p>
 */
public class RuleFilter {

    /** 规则置信度阈值：≥ 该值跳过模型精判，&lt; 该值调用模型精判。 */
    public static final double CONFIDENCE_THRESHOLD = 0.9;

    /** 关键词明确匹配置信度（≥ 阈值，跳过模型精判）。 */
    public static final double KEYWORD_MATCH_CONFIDENCE = 0.95;

    /** 仅长度规则匹配的模糊置信度（&lt; 阈值，需模型精判）。 */
    public static final double LENGTH_RULE_CONFIDENCE = 0.85;

    /** 无任何规则匹配置信度（&lt; 阈值，需模型精判）。 */
    public static final double NO_MATCH_CONFIDENCE = 0.5;

    /** 长度规则阈值：goal 字符数 &lt; 该值触发长度规则。 */
    public static final int LENGTH_THRESHOLD = 20;

    /** L1 关键词集（简单任务）。 */
    private static final List<String> L1_KEYWORDS = List.of("查询", "翻译", "总结");

    /** L2 关键词集（中等任务）。 */
    private static final List<String> L2_KEYWORDS = List.of("分析", "对比", "生成报告");

    /** L3 关键词集（复杂任务）。 */
    private static final List<String> L3_KEYWORDS = List.of("编排", "协同", "跨系统");

    /**
     * 对 goal 文本做规则初筛，返回候选等级 + 置信度。
     *
     * @param goal 任务目标文本（可为 null 或空）
     * @return 初筛结果，调用方可通过 {@link Result#needsModelAssessment()} 判断是否需模型精判
     */
    public Result quickFilter(String goal) {
        // null / 空 goal：无候选，需模型精判
        if (goal == null || goal.isEmpty()) {
            return new Result(null, NO_MATCH_CONFIDENCE, "no-match:empty-or-null");
        }

        // 优先级 L3 > L2 > L1：高复杂度关键词优先匹配
        String l3Keyword = matchKeyword(goal, L3_KEYWORDS);
        if (l3Keyword != null) {
            return new Result(ComplexityLevel.L3, KEYWORD_MATCH_CONFIDENCE,
                    "keyword:L3:" + l3Keyword);
        }

        String l2Keyword = matchKeyword(goal, L2_KEYWORDS);
        if (l2Keyword != null) {
            return new Result(ComplexityLevel.L2, KEYWORD_MATCH_CONFIDENCE,
                    "keyword:L2:" + l2Keyword);
        }

        String l1Keyword = matchKeyword(goal, L1_KEYWORDS);
        if (l1Keyword != null) {
            return new Result(ComplexityLevel.L1, KEYWORD_MATCH_CONFIDENCE,
                    "keyword:L1:" + l1Keyword);
        }

        // 长度规则：goal 字符数 < 阈值 → L1 候选（模糊匹配，需模型精判）
        if (goal.length() < LENGTH_THRESHOLD) {
            return new Result(ComplexityLevel.L1, LENGTH_RULE_CONFIDENCE,
                    "length:<" + LENGTH_THRESHOLD);
        }

        // 无匹配：候选为 null，需模型精判
        return new Result(null, NO_MATCH_CONFIDENCE, "no-match");
    }

    /**
     * 在 goal 中查找首个命中的关键词。
     *
     * @param goal     任务目标文本
     * @param keywords 关键词列表
     * @return 命中的关键词；未命中返回 null
     */
    private String matchKeyword(String goal, List<String> keywords) {
        for (String keyword : keywords) {
            if (goal.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    /**
     * 规则初筛结果。
     *
     * <p>不可变对象。通过 {@link #needsModelAssessment()} 判定是否需要调用 ModelAssessor。</p>
     */
    public static final class Result {

        private final ComplexityLevel candidateLevel;
        private final double confidence;
        private final String matchedRule;

        /**
         * 构造初筛结果。
         *
         * @param candidateLevel 候选等级（可为 null，表示无候选）
         * @param confidence     置信度 [0.0, 1.0]
         * @param matchedRule    命中的规则描述（用于调试/审计）
         */
        public Result(ComplexityLevel candidateLevel, double confidence, String matchedRule) {
            this.candidateLevel = candidateLevel;
            this.confidence = confidence;
            this.matchedRule = matchedRule;
        }

        /**
         * @return 候选等级（可为 null，表示无候选，需模型精判）
         */
        public ComplexityLevel getCandidateLevel() {
            return candidateLevel;
        }

        /**
         * @return 置信度 [0.0, 1.0]
         */
        public double getConfidence() {
            return confidence;
        }

        /**
         * @return 命中的规则描述（如 "keyword:L3:编排" / "length:<20" / "no-match"）
         */
        public String getMatchedRule() {
            return matchedRule;
        }

        /**
         * 是否需要调用 ModelAssessor 模型精判。
         *
         * <p>判定逻辑：置信度 &lt; {@link #CONFIDENCE_THRESHOLD} 时需要模型精判。
         * 即明确匹配（关键词命中）可跳过模型，模糊匹配（仅长度规则/无匹配）需模型。</p>
         *
         * @return true 表示应调用 ModelAssessor；false 表示可跳过模型精判
         */
        public boolean needsModelAssessment() {
            return confidence < CONFIDENCE_THRESHOLD;
        }
    }
}
