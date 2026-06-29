package com.agent.orchestrator.assessor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RuleFilter 单元测试（Red 阶段：实现尚未存在，预期编译失败）。
 *
 * <p>对齐 doc 03-task-engine §2.3 两级识别流程 Stage 1（规则初筛）。
 * 测试覆盖 docs/tests/unit-test-cases.md §6 UT-PLAN-005/006 + 长度/关键词/置信度规则。</p>
 *
 * <p>规则集（来自设计文档 §2.3）：</p>
 * <ul>
 *   <li>长度规则：goal 字符数 &lt; 20 → L1 候选（模糊匹配，置信度 &lt; 0.9）</li>
 *   <li>关键词规则（明确匹配，置信度 ≥ 0.9）：
 *     <ul>
 *       <li>L1: "查询" / "翻译" / "总结"</li>
 *       <li>L2: "分析" / "对比" / "生成报告"</li>
 *       <li>L3: "编排" / "协同" / "跨系统"</li>
 *     </ul>
 *   </li>
 *   <li>无匹配：candidateLevel=null，置信度 &lt; 0.9</li>
 * </ul>
 *
 * <p>置信度阈值 = 0.9（对齐 doc 03-task-engine §11
 * task.planning.ruleFilterConfidenceThreshold）。</p>
 *
 * <p>判定逻辑：置信度 ≥ 0.9 跳过模型精判（bypass ModelAssessor）；
 * 置信度 &lt; 0.9 调用模型精判（invoke ModelAssessor）。</p>
 */
class RuleFilterTest {

    private final RuleFilter ruleFilter = new RuleFilter();

    // ===== UT-PLAN-005: 规则置信度 ≥ 0.9 跳过模型精判 =====

    @Test
    @DisplayName("UT-PLAN-005: 规则置信度=0.95（≥0.9）应跳过模型精判")
    void should_BypassModelAssessor_When_RuleConfidenceHigh() {
        // goal 含 L1 关键词"查询"，明确匹配 → 置信度 ≥ 0.9
        RuleFilter.Result result = ruleFilter.quickFilter("查询订单状态");

        assertThat(result.getConfidence())
                .as("关键词匹配应给出明确置信度 ≥ 0.9").isGreaterThanOrEqualTo(0.9);
        assertThat(result.needsModelAssessment())
                .as("置信度 ≥ 0.9 时应跳过模型精判").isFalse();
    }

    // ===== UT-PLAN-006: 规则置信度 < 0.9 调用模型精判 =====

    @Test
    @DisplayName("UT-PLAN-006: 规则置信度<0.9 应调用模型精判")
    void should_InvokeModelAssessor_When_RuleConfidenceLow() {
        // goal 长度=22(≥20) 且无关键词匹配 → 无明确规则 → 置信度 < 0.9
        RuleFilter.Result result = ruleFilter.quickFilter("请帮我处理一下这个比较复杂的业务问题非常感谢");

        assertThat(result.getConfidence())
                .as("无明确规则匹配时置信度应 < 0.9").isLessThan(0.9);
        assertThat(result.needsModelAssessment())
                .as("置信度 < 0.9 时应调用模型精判").isTrue();
    }

    // ===== 长度规则：goal < 20 字符 → L1 候选 =====

    @Test
    @DisplayName("长度规则: goal < 20 字符应返回 L1 候选")
    void should_ReturnL1Candidate_When_GoalLengthLessThan20() {
        // 6 个字符，无关键词匹配，触发长度规则
        RuleFilter.Result result = ruleFilter.quickFilter("你好世界今天");

        assertThat(result.getCandidateLevel())
                .as("短 goal 应返回 L1 候选").isEqualTo(ComplexityLevel.L1);
        assertThat(result.getMatchedRule())
                .as("应匹配长度规则").contains("length");
    }

    @Test
    @DisplayName("长度规则边界: goal 恰好 19 字符应返回 L1 候选")
    void should_ReturnL1Candidate_When_GoalLengthEquals19() {
        // 19 字符（边界值，< 20）
        String goal = "一二三四五六七八九十一二三四五六七八九";
        assertThat(goal.length()).as("校验 goal 长度=19").isEqualTo(19);

        RuleFilter.Result result = ruleFilter.quickFilter(goal);

        assertThat(result.getCandidateLevel())
                .as("goal 长度=19 应返回 L1 候选").isEqualTo(ComplexityLevel.L1);
    }

    @Test
    @DisplayName("长度规则边界: goal 恰好 20 字符不应触发长度规则")
    void should_NotTriggerLengthRule_When_GoalLengthEquals20() {
        // 20 字符（边界值，不 < 20），无关键词 → 候选为 null
        // "测试" 重复 10 次 = 20 字符，不含任何关键词
        String goal = "测试".repeat(10);
        assertThat(goal.length()).as("校验 goal 长度=20").isEqualTo(20);

        RuleFilter.Result result = ruleFilter.quickFilter(goal);

        assertThat(result.getCandidateLevel())
                .as("goal 长度=20 不触发长度规则，无候选").isNull();
        assertThat(result.needsModelAssessment())
                .as("无规则匹配应调用模型精判").isTrue();
    }

    // ===== 关键词规则：L1（查询/翻译/总结） =====

    @Test
    @DisplayName("关键词规则: goal 含'查询'应返回 L1 候选")
    void should_ReturnL1Candidate_When_GoalContainsQueryKeyword() {
        RuleFilter.Result result = ruleFilter.quickFilter("查询用户最近一周的订单数据");

        assertThat(result.getCandidateLevel())
                .as("含'查询'关键词应返回 L1 候选").isEqualTo(ComplexityLevel.L1);
    }

    @Test
    @DisplayName("关键词规则: goal 含'翻译'应返回 L1 候选")
    void should_ReturnL1Candidate_When_GoalContainsTranslateKeyword() {
        RuleFilter.Result result = ruleFilter.quickFilter("请把这段中文翻译成英文");

        assertThat(result.getCandidateLevel())
                .as("含'翻译'关键词应返回 L1 候选").isEqualTo(ComplexityLevel.L1);
    }

    @Test
    @DisplayName("关键词规则: goal 含'总结'应返回 L1 候选")
    void should_ReturnL1Candidate_When_GoalContainsSummaryKeyword() {
        RuleFilter.Result result = ruleFilter.quickFilter("总结本次会议的要点");

        assertThat(result.getCandidateLevel())
                .as("含'总结'关键词应返回 L1 候选").isEqualTo(ComplexityLevel.L1);
    }

    // ===== 关键词规则：L2（分析/对比/生成报告） =====

    @Test
    @DisplayName("关键词规则: goal 含'分析'应返回 L2 候选")
    void should_ReturnL2Candidate_When_GoalContainsAnalysisKeyword() {
        RuleFilter.Result result = ruleFilter.quickFilter("分析最近一个月的销售数据趋势");

        assertThat(result.getCandidateLevel())
                .as("含'分析'关键词应返回 L2 候选").isEqualTo(ComplexityLevel.L2);
    }

    @Test
    @DisplayName("关键词规则: goal 含'对比'应返回 L2 候选")
    void should_ReturnL2Candidate_When_GoalContainsCompareKeyword() {
        RuleFilter.Result result = ruleFilter.quickFilter("对比两个产品的功能差异");

        assertThat(result.getCandidateLevel())
                .as("含'对比'关键词应返回 L2 候选").isEqualTo(ComplexityLevel.L2);
    }

    @Test
    @DisplayName("关键词规则: goal 含'生成报告'应返回 L2 候选")
    void should_ReturnL2Candidate_When_GoalContainsGenerateReportKeyword() {
        RuleFilter.Result result = ruleFilter.quickFilter("根据本月数据生成报告");

        assertThat(result.getCandidateLevel())
                .as("含'生成报告'关键词应返回 L2 候选").isEqualTo(ComplexityLevel.L2);
    }

    // ===== 关键词规则：L3（编排/协同/跨系统） =====

    @Test
    @DisplayName("关键词规则: goal 含'编排'应返回 L3 候选")
    void should_ReturnL3Candidate_When_GoalContainsOrchestrationKeyword() {
        RuleFilter.Result result = ruleFilter.quickFilter("编排多个子任务形成完整工作流");

        assertThat(result.getCandidateLevel())
                .as("含'编排'关键词应返回 L3 候选").isEqualTo(ComplexityLevel.L3);
    }

    @Test
    @DisplayName("关键词规则: goal 含'协同'应返回 L3 候选")
    void should_ReturnL3Candidate_When_GoalContainsCollaborationKeyword() {
        RuleFilter.Result result = ruleFilter.quickFilter("多 Agent 协同完成复杂任务");

        assertThat(result.getCandidateLevel())
                .as("含'协同'关键词应返回 L3 候选").isEqualTo(ComplexityLevel.L3);
    }

    @Test
    @DisplayName("关键词规则: goal 含'跨系统'应返回 L3 候选")
    void should_ReturnL3Candidate_When_GoalContainsCrossSystemKeyword() {
        RuleFilter.Result result = ruleFilter.quickFilter("跨系统同步用户权限与角色数据");

        assertThat(result.getCandidateLevel())
                .as("含'跨系统'关键词应返回 L3 候选").isEqualTo(ComplexityLevel.L3);
    }

    // ===== 优先级：L3 > L2 > L1 =====

    @Test
    @DisplayName("优先级: goal 同时含 L1 和 L3 关键词应返回 L3（高复杂度优先）")
    void should_ReturnL3_When_GoalContainsBothL1AndL3Keywords() {
        // 同时含"查询"(L1) 和"编排"(L3) → L3 优先
        RuleFilter.Result result = ruleFilter.quickFilter("编排任务并查询相关数据");

        assertThat(result.getCandidateLevel())
                .as("L3 关键词优先级高于 L1").isEqualTo(ComplexityLevel.L3);
    }

    @Test
    @DisplayName("优先级: goal 同时含 L1 和 L2 关键词应返回 L2（高复杂度优先）")
    void should_ReturnL2_When_GoalContainsBothL1AndL2Keywords() {
        // 同时含"总结"(L1) 和"分析"(L2) → L2 优先
        RuleFilter.Result result = ruleFilter.quickFilter("分析数据并总结结论");

        assertThat(result.getCandidateLevel())
                .as("L2 关键词优先级高于 L1").isEqualTo(ComplexityLevel.L2);
    }

    // ===== 置信度：明确匹配 ≥ 0.9，模糊匹配 < 0.9 =====

    @Test
    @DisplayName("置信度: 关键词明确匹配应返回 ≥ 0.9 的置信度")
    void should_ReturnHighConfidence_When_ExplicitKeywordMatched() {
        RuleFilter.Result result = ruleFilter.quickFilter("查询订单");

        assertThat(result.getConfidence())
                .as("关键词匹配（明确）置信度应 ≥ 0.9").isGreaterThanOrEqualTo(0.9);
        assertThat(result.needsModelAssessment())
                .as("明确匹配应跳过模型精判").isFalse();
    }

    @Test
    @DisplayName("置信度: 仅长度规则匹配应返回 < 0.9 的置信度（模糊匹配）")
    void should_ReturnLowConfidence_When_OnlyLengthRuleMatched() {
        // 短 goal 无关键词 → 仅长度规则 → 模糊匹配
        RuleFilter.Result result = ruleFilter.quickFilter("你好世界");

        assertThat(result.getConfidence())
                .as("仅长度规则（模糊）置信度应 < 0.9").isLessThan(0.9);
        assertThat(result.needsModelAssessment())
                .as("模糊匹配应调用模型精判").isTrue();
    }

    @Test
    @DisplayName("置信度: 无任何规则匹配应返回 < 0.9 的置信度")
    void should_ReturnLowConfidence_When_NoRuleMatched() {
        // goal 长度=22(≥20) 无关键词 → 无匹配
        RuleFilter.Result result = ruleFilter.quickFilter("请帮我处理一下这个比较复杂的业务问题非常感谢");

        assertThat(result.getConfidence())
                .as("无规则匹配置信度应 < 0.9").isLessThan(0.9);
        assertThat(result.getCandidateLevel())
                .as("无匹配时候选等级为 null").isNull();
        assertThat(result.needsModelAssessment())
                .as("无匹配应调用模型精判").isTrue();
    }

    // ===== 边界：null / 空 goal =====

    @Test
    @DisplayName("边界: null goal 应返回无候选且需模型精判")
    void should_ReturnNoCandidate_When_GoalIsNull() {
        RuleFilter.Result result = ruleFilter.quickFilter(null);

        assertThat(result.getCandidateLevel())
                .as("null goal 时候选为 null").isNull();
        assertThat(result.needsModelAssessment())
                .as("null goal 应调用模型精判").isTrue();
    }

    @Test
    @DisplayName("边界: 空 goal 应返回无候选且需模型精判")
    void should_ReturnNoCandidate_When_GoalIsEmpty() {
        RuleFilter.Result result = ruleFilter.quickFilter("");

        assertThat(result.getCandidateLevel())
                .as("空 goal 时候选为 null").isNull();
        assertThat(result.needsModelAssessment())
                .as("空 goal 应调用模型精判").isTrue();
    }
}
