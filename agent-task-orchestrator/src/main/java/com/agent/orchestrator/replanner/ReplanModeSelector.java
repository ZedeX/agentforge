package com.agent.orchestrator.replanner;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重规划模式选择器（对齐 doc 03-task-engine §5.1/§5.3 + F5.D2/D3/D4 决策节点）。
 *
 * <p>根据失败场景在 {@link ReplanMode#INCREMENTAL} / {@link ReplanMode#FULL} 之间选择，
 * 当重规划次数耗尽时抛 {@link ErrorCode#REPLAN_EXHAUSTED} 或通过 {@link #selectOrAbort}
 * 返回 {@link ReplanMode#ABORT}。</p>
 *
 * <p>选择规则（按优先级从高到低）：</p>
 * <ol>
 *   <li><b>熔断检查</b>：{@code replanCount > maxReplan} → 抛 {@link ErrorCode#REPLAN_EXHAUSTED}
 *       （F5.D4 true 分支，对应 UT-ORCH-011）。熔断检查优先于其他规则，避免在已耗尽配额时
 *       仍触发新的重规划。</li>
 *   <li><b>需求变更</b>：{@code triggerReason = "requirement_change"} → {@link ReplanMode#FULL}
 *       （F5.D2 true 分支，对应 UT-ORCH-010）。需求变更影响根节点，仅靠增量重规划无法修复。</li>
 *   <li><b>全部失败</b>：{@code failedCount >= totalCount} → {@link ReplanMode#FULL}。
 *       全部子任务失败意味着 DAG 整体失效，必须全量重规划。</li>
 *   <li><b>失败过半</b>：{@code failedCount > totalCount / 2} → {@link ReplanMode#FULL}
 *       （参考 UT-ORCH-013）。失败过半判定为致命异常，触发全量重规划或人工介入。</li>
 *   <li><b>少数失败 + 其余有效</b>：{@code failedCount > 0} 且 {@code otherOutputsValid = true}
 *       → {@link ReplanMode#INCREMENTAL} （F5.D3 true 分支，对应 UT-ORCH-009）。</li>
 *   <li><b>默认</b>：{@link ReplanMode#FULL}（无法判定时优先全量以保证一致性）。</li>
 * </ol>
 *
 * <p>设计说明：本类为纯函数式选择器，不持有状态；调用方负责传入 {@link ReplanContext}。
 * 这样便于单测覆盖所有分支，且与未来可能的策略模式扩展（如基于成本预算选择 mode）解耦。</p>
 */
public class ReplanModeSelector {

    /**
     * 需求变更触发原因常量（对应 doc 03 §5.1 表中"用户需求变更"行）。
     */
    public static final String REASON_REQUIREMENT_CHANGE = "requirement_change";

    /**
     * 根据重规划上下文选择模式。
     *
     * @param context 重规划上下文（不可为 null）
     * @return {@link ReplanMode#INCREMENTAL} 或 {@link ReplanMode#FULL}
     * @throws BusinessException 当 {@code replanCount > maxReplan} 时抛
     *         {@link ErrorCode#REPLAN_EXHAUSTED}
     */
    public ReplanMode select(ReplanContext context) {
        if (context == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "ReplanContext 不可为 null");
        }

        // 1. 熔断检查（最高优先级）
        if (context.getReplanCount() > context.getMaxReplan()) {
            throw new BusinessException(ErrorCode.REPLAN_EXHAUSTED,
                    "重规划次数耗尽：replan_count=" + context.getReplanCount()
                            + ", max_replan=" + context.getMaxReplan());
        }

        // 2. 需求变更 → 全量重规划
        if (REASON_REQUIREMENT_CHANGE.equals(context.getTriggerReason())) {
            return ReplanMode.FULL;
        }

        // 3. 全部失败 → 全量重规划
        if (context.getFailedCount() >= context.getTotalCount()) {
            return ReplanMode.FULL;
        }

        // 4. 失败过半 → 全量重规划（致命异常）
        if (context.getFailedCount() > context.getTotalCount() / 2) {
            return ReplanMode.FULL;
        }

        // 5. 少数失败 + 其余输出有效 → 增量重规划
        if (context.getFailedCount() > 0 && context.isOtherOutputsValid()) {
            return ReplanMode.INCREMENTAL;
        }

        // 6. 默认全量重规划（无法判定时优先一致性）
        return ReplanMode.FULL;
    }

    /**
     * 同 {@link #select(ReplanContext)}，但当熔断时不抛异常而返回 {@link ReplanMode#ABORT}。
     *
     * <p>调用方在状态机层应将 {@link ReplanMode#ABORT} 映射为 {@code WAITING_HUMAN}
     * 状态流转（对应 UT-ORCH-011 中"转 WAITING_HUMAN"语义）。</p>
     *
     * @param context 重规划上下文
     * @return {@link ReplanMode} 三选一
     */
    public ReplanMode selectOrAbort(ReplanContext context) {
        try {
            return select(context);
        } catch (BusinessException ex) {
            if (ErrorCode.REPLAN_EXHAUSTED.equals(ex.getErrorCode())) {
                return ReplanMode.ABORT;
            }
            throw ex;
        }
    }

    /**
     * 重规划上下文 POJO。
     *
     * <p>使用 Lombok {@code @Data + @Builder + @AllArgsConstructor + @NoArgsConstructor}
     * 以兼容反射框架与单测构造便利性。</p>
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReplanContext {

        /**
         * 失败子任务数量。
         */
        private int failedCount;

        /**
         * 子任务总数。
         */
        private int totalCount;

        /**
         * 其余（未失败的）子任务输出是否仍然有效。
         * <p>true 表示可复用 frozenNodes 触发增量重规划；false 表示需要全量重规划。</p>
         */
        private boolean otherOutputsValid;

        /**
         * 触发原因（对应 doc 03 §5.1 表）。
         * <p>取值如 {@code "subtask_failed"} / {@code "requirement_change"} /
         * {@code "cost_overrun"} / {@code "cross_domain"} 等。</p>
         */
        private String triggerReason;

        /**
         * 当前已重规划次数（含本次待触发）。
         */
        private int replanCount;

        /**
         * 单任务最大重规划次数上限（默认 2，对应 doc 11 F5.D4 max_replan=2）。
         */
        private int maxReplan;
    }
}
