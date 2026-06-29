package com.agent.orchestrator.template;

import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TemplateMatcher 单元测试（对齐 doc 03-task-engine §3.2 + UT-PLAN-007/008）。
 *
 * <p>覆盖 8 类场景：</p>
 * <ul>
 *   <li>UT-PLAN-007: 高频场景匹配预置模板（goal="生成周报"）</li>
 *   <li>UT-PLAN-008: 无模板匹配进入智能规划（goal="个性化长尾需求"）</li>
 *   <li>边界1: sceneTags 为空列表 → 返回 null</li>
 *   <li>边界2: sceneTags 为 null → 返回 null</li>
 *   <li>边界3: 模板 status!=2（禁用）→ 不匹配</li>
 *   <li>边界4: 模板 successRate&lt;0.85 → 不匹配</li>
 *   <li>边界5: 多个匹配模板按 usageCount DESC 排序，返回使用次数最多的</li>
 *   <li>边界6: 模板列表为空 → 返回 null</li>
 * </ul>
 *
 * <p>TDD 流程：Red 阶段（match 骨架返回 null）→ Green 阶段（补全过滤 + 排序 + 参数校验）。
 * 本测试类同时充当 UT-PLAN-007/008 的载体，并验证 {@link PlanMode#fromMatched} 推断。</p>
 */
class TemplateMatcherTest {

    /** 周报模板 DAG 节点（用于校验返回的模板 DAG 非空）。 */
    private static final List<DagNode> WEEKLY_NODES = Collections.singletonList(
            DagNode.builder()
                    .dagId(1L)
                    .nodeId("n1")
                    .nodeType("task")
                    .title("汇总本周工作")
                    .status("pending")
                    .build());

    /** 周报模板 DAG 边。 */
    private static final List<DagEdge> WEEKLY_EDGES = Collections.emptyList();

    /**
     * UT-PLAN-007: 高频场景匹配预置模板。
     * <p>task.goal="生成周报"，sceneTags=["report","weekly"] 命中周报模板，
     * 返回模板 DAG 且 mode=TEMPLATE。</p>
     */
    @Test
    @DisplayName("UT-PLAN-007: 高频场景匹配预置模板时返回模板 DAG 且 mode=TEMPLATE")
    void should_MatchTemplate_When_HighFrequencyScenario() {
        TaskTemplate weekly = TaskTemplate.builder()
                .templateId("tpl-weekly-report")
                .title("周报生成模板")
                .sceneTags(Arrays.asList("report", "weekly"))
                .paramSchema("{\"entity\":\"${entity}\"}")
                .dagNodes(WEEKLY_NODES)
                .dagEdges(WEEKLY_EDGES)
                .successRate(0.92)
                .usageCount(100)
                .status(2)
                .build();
        TemplateMatcher matcher = new TemplateMatcher(Collections.singletonList(weekly));

        TaskTemplate matched = matcher.match("{\"goal\":\"生成周报\"}",
                Arrays.asList("report", "weekly"));

        assertThat(matched).as("高频场景应命中周报模板").isNotNull();
        assertThat(matched.getTemplateId())
                .as("应返回周报模板 ID").isEqualTo("tpl-weekly-report");
        assertThat(matched.getDagNodes())
                .as("模板应包含 DAG 节点").isNotNull().isNotEmpty();
        assertThat(PlanMode.fromMatched(matched))
                .as("命中模板时 mode 应为 TEMPLATE").isEqualTo(PlanMode.TEMPLATE);
    }

    /**
     * UT-PLAN-008: 无模板匹配进入智能规划。
     * <p>task.goal="个性化长尾需求"，sceneTags 与任何模板不匹配 → 返回 null，
     * mode=AI（走智能规划分支）。</p>
     */
    @Test
    @DisplayName("UT-PLAN-008: 无模板匹配时返回 null 且 mode=AI（走智能规划）")
    void should_FallbackToAiPlanner_When_NoTemplateMatched() {
        TaskTemplate weekly = TaskTemplate.builder()
                .templateId("tpl-weekly-report")
                .title("周报生成模板")
                .sceneTags(Arrays.asList("report", "weekly"))
                .paramSchema("{}")
                .dagNodes(WEEKLY_NODES)
                .dagEdges(WEEKLY_EDGES)
                .successRate(0.92)
                .usageCount(100)
                .status(2)
                .build();
        TemplateMatcher matcher = new TemplateMatcher(Collections.singletonList(weekly));

        // 长尾需求场景标签不匹配任何模板
        TaskTemplate matched = matcher.match("{\"goal\":\"个性化长尾需求\"}",
                Arrays.asList("longtail", "personal"));

        assertThat(matched).as("无匹配时应返回 null 走智能规划").isNull();
        assertThat(PlanMode.fromMatched(matched))
                .as("无匹配时 mode 应为 AI").isEqualTo(PlanMode.AI);
    }

    /**
     * 边界1: sceneTags 为空列表 → 返回 null。
     */
    @Test
    @DisplayName("边界1: sceneTags 为空列表时返回 null")
    void should_ReturnNull_When_SceneTagsIsEmpty() {
        TaskTemplate tpl = enabledTemplate("tpl-1", Arrays.asList("report"), 0.9, 10);
        TemplateMatcher matcher = new TemplateMatcher(Collections.singletonList(tpl));

        TaskTemplate matched = matcher.match("{\"goal\":\"x\"}", Collections.emptyList());

        assertThat(matched).as("空 sceneTags 不应匹配任何模板").isNull();
    }

    /**
     * 边界2: sceneTags 为 null → 返回 null。
     */
    @Test
    @DisplayName("边界2: sceneTags 为 null 时返回 null")
    void should_ReturnNull_When_SceneTagsIsNull() {
        TaskTemplate tpl = enabledTemplate("tpl-1", Arrays.asList("report"), 0.9, 10);
        TemplateMatcher matcher = new TemplateMatcher(Collections.singletonList(tpl));

        TaskTemplate matched = matcher.match("{\"goal\":\"x\"}", null);

        assertThat(matched).as("null sceneTags 不应匹配任何模板").isNull();
    }

    /**
     * 边界3: 模板 status!=2（禁用）时不参与匹配。
     */
    @Test
    @DisplayName("边界3: 模板 status!=2（禁用）时不参与匹配")
    void should_ReturnNull_When_TemplateStatusDisabled() {
        // status=1（禁用）即使 sceneTags 命中也应被过滤
        TaskTemplate disabled = TaskTemplate.builder()
                .templateId("tpl-disabled")
                .title("已禁用模板")
                .sceneTags(Arrays.asList("report"))
                .paramSchema("{}")
                .dagNodes(WEEKLY_NODES)
                .dagEdges(WEEKLY_EDGES)
                .successRate(0.9)
                .usageCount(10)
                .status(1)  // 禁用
                .build();
        TemplateMatcher matcher = new TemplateMatcher(Collections.singletonList(disabled));

        TaskTemplate matched = matcher.match("{\"goal\":\"x\"}", Arrays.asList("report"));

        assertThat(matched).as("禁用模板不应被匹配").isNull();
    }

    /**
     * 边界4: 模板 successRate&lt;0.85 时不参与匹配。
     */
    @Test
    @DisplayName("边界4: 模板 successRate<0.85 时不参与匹配")
    void should_ReturnNull_When_SuccessRateBelowThreshold() {
        // successRate=0.80 < 0.85，即使 sceneTags 命中也应被过滤
        TaskTemplate low = TaskTemplate.builder()
                .templateId("tpl-low-success")
                .title("低成功率模板")
                .sceneTags(Arrays.asList("report"))
                .paramSchema("{}")
                .dagNodes(WEEKLY_NODES)
                .dagEdges(WEEKLY_EDGES)
                .successRate(0.80)
                .usageCount(10)
                .status(2)
                .build();
        TemplateMatcher matcher = new TemplateMatcher(Collections.singletonList(low));

        TaskTemplate matched = matcher.match("{\"goal\":\"x\"}", Arrays.asList("report"));

        assertThat(matched).as("低成功率模板不应被匹配").isNull();
    }

    /**
     * 边界5: 多个匹配模板按 usageCount DESC 排序，返回使用次数最多的。
     */
    @Test
    @DisplayName("边界5: 多个匹配模板按 usageCount DESC 排序，返回使用次数最多的")
    void should_ReturnHighestUsageTemplate_When_MultipleCandidatesMatch() {
        TaskTemplate lessUsed = enabledTemplate("tpl-less", Arrays.asList("report"), 0.90, 50);
        TaskTemplate mostUsed = enabledTemplate("tpl-most", Arrays.asList("report"), 0.92, 200);
        TaskTemplate midUsed = enabledTemplate("tpl-mid", Arrays.asList("report"), 0.95, 100);
        // 故意乱序传入，验证排序后返回 usageCount 最大的
        TemplateMatcher matcher = new TemplateMatcher(Arrays.asList(lessUsed, mostUsed, midUsed));

        TaskTemplate matched = matcher.match("{\"goal\":\"x\"}", Arrays.asList("report"));

        assertThat(matched).as("多候选应返回使用次数最多的模板").isNotNull();
        assertThat(matched.getTemplateId())
                .as("使用次数最多（200）的模板应优先返回").isEqualTo("tpl-most");
    }

    /**
     * 边界6: 模板列表为空 → 返回 null。
     */
    @Test
    @DisplayName("边界6: 模板列表为空时返回 null")
    void should_ReturnNull_When_TemplateListEmpty() {
        TemplateMatcher matcher = new TemplateMatcher(Collections.emptyList());

        TaskTemplate matched = matcher.match("{\"goal\":\"x\"}", Arrays.asList("report"));

        assertThat(matched).as("空模板列表不应匹配任何模板").isNull();
    }

    /**
     * 辅助：构造一个启用且成功率高于阈值的模板（减少重复样板代码）。
     */
    private TaskTemplate enabledTemplate(String id, List<String> sceneTags,
                                         double successRate, int usageCount) {
        return TaskTemplate.builder()
                .templateId(id)
                .title("模板-" + id)
                .sceneTags(sceneTags)
                .paramSchema("{}")
                .dagNodes(WEEKLY_NODES)
                .dagEdges(WEEKLY_EDGES)
                .successRate(successRate)
                .usageCount(usageCount)
                .status(2)
                .build();
    }
}
