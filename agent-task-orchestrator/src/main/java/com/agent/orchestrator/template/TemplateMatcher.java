package com.agent.orchestrator.template;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模板匹配器（对齐 doc 03-task-engine §3.2 模板匹配算法）。
 *
 * <p>实现两步匹配：</p>
 * <ol>
 *   <li><b>第一步</b>：按 scene_tags 精确匹配 —— 过滤 {@code status==2} 且
 *       {@code successRate&gt;=0.85} 的模板，取与 task.scene_tags 交集非空的候选集，
 *       按 {@code usage_count DESC, success_rate DESC} 排序。</li>
 *   <li><b>第二步</b>：参数 schema 兼容性校验 —— 按排序依次校验，返回首个兼容的模板；
 *       无兼容返回 {@code null}。</li>
 * </ol>
 *
 * <p>设计说明：构造注入 {@code List<TaskTemplate>}（不依赖数据库，纯 POJO），
 * 便于单测覆盖；参数 schema 兼容性校验简化为"taskSchema 非空即兼容"
 * （后续可扩展为基于 JSON Schema 的字段比对）。{@link #match} 返回 {@code null}
 * 即表示走智能规划（AI 分支），调用方可通过 {@link PlanMode#fromMatched} 推断 mode。</p>
 *
 * <p>TDD 流程：本类对应 UT-PLAN-007/008 用例。Red 阶段（match 直接 return null）
 * → Green 阶段（补全过滤 + 排序 + 参数校验逻辑）。</p>
 */
public class TemplateMatcher {

    /** 模板启用状态常量（对应 doc §3.2 status=2）。 */
    public static final int STATUS_ENABLED = 2;

    /** 成功率阈值（对应 doc §3.2 success_rate&gt;=0.85）。 */
    public static final double SUCCESS_RATE_THRESHOLD = 0.85;

    private final List<TaskTemplate> templates;

    /**
     * 构造注入模板列表。
     *
     * @param templates 模板列表（不可为 null；为空时 match 始终返回 null）
     */
    public TemplateMatcher(List<TaskTemplate> templates) {
        this.templates = templates == null ? Collections.emptyList() : templates;
    }

    /**
     * 匹配最合适的模板。
     *
     * @param taskSchema 任务 schema（JSON 字符串，简化策略：非空即视为参数兼容）
     * @param sceneTags  任务场景标签列表（null 或空直接返回 null）
     * @return 命中的模板；无匹配返回 {@code null}（走智能规划）
     */
    public TaskTemplate match(String taskSchema, List<String> sceneTags) {
        // 1. sceneTags 为空 / null → 直接走 AI
        if (sceneTags == null || sceneTags.isEmpty()) {
            return null;
        }
        if (templates.isEmpty()) {
            return null;
        }

        // 2. 第一步：候选集筛选（status==2 + successRate>=0.85 + sceneTags 交集非空）
        List<TaskTemplate> candidates = templates.stream()
                .filter(t -> t.getStatus() == STATUS_ENABLED)
                .filter(t -> t.getSuccessRate() >= SUCCESS_RATE_THRESHOLD)
                .filter(t -> intersectNonEmpty(t.getSceneTags(), sceneTags))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null;
        }

        // 3. 排序：usage_count DESC, success_rate DESC
        Comparator<TaskTemplate> byUsageDesc = Comparator
                .comparingInt(TaskTemplate::getUsageCount).reversed();
        Comparator<TaskTemplate> bySuccessDesc = Comparator
                .comparingDouble(TaskTemplate::getSuccessRate).reversed();
        candidates.sort(byUsageDesc.thenComparing(bySuccessDesc));

        // 4. 第二步：参数 schema 兼容性校验（简化：taskSchema 非空即兼容）
        for (TaskTemplate t : candidates) {
            if (validateParams(taskSchema, t.getParamSchema())) {
                return t;
            }
        }

        return null;
    }

    /**
     * 参数 schema 兼容性校验（简化版）。
     *
     * <p>简化策略：taskSchema 非空即视为兼容。后续可替换为基于 JSON Schema 的
     * 字段比对实现（如检查必填字段、类型匹配等）。</p>
     *
     * @param taskSchema  任务 schema
     * @param paramSchema 模板参数 schema（当前简化版本不参与判定，保留参数以兼容未来扩展）
     * @return true 表示兼容
     */
    private boolean validateParams(String taskSchema, String paramSchema) {
        return taskSchema != null && !taskSchema.isEmpty();
    }

    /**
     * 判断两个 sceneTags 列表交集是否非空。
     *
     * @param templateTags 模板场景标签
     * @param taskTags     任务场景标签
     * @return true 表示存在交集
     */
    private boolean intersectNonEmpty(List<String> templateTags, List<String> taskTags) {
        if (templateTags == null || templateTags.isEmpty()) {
            return false;
        }
        // 场景标签数量通常很小，contains 即可；若未来扩展为大列表可换 HashSet
        for (String tag : taskTags) {
            if (templateTags.contains(tag)) {
                return true;
            }
        }
        return false;
    }
}
