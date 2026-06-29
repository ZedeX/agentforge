package com.agent.orchestrator.assessor;

/**
 * 任务复杂度等级（doc 03-task-engine §2.1 三级分级标准）。
 *
 * <p>本枚举用于 {@link ComplexityScorer} 评分结果与 {@link RuleFilter} 候选等级。
 * 与 {@code com.agent.common.constant.ComplexityLevel} 区别：
 * agent-common 中的枚举携带 stepRange/toolRange/costLimitCent 等运行时配置，
 * 本枚举为评估器内部的纯等级标识，仅表达 L1/L2/L3 语义。</p>
 *
 * <ul>
 *   <li>{@link #L1} — 简单任务：单目标单步、无需工具与外部知识，单 Agent 直出</li>
 *   <li>{@link #L2} — 中等任务：单领域、1~3 次工具调用，单 Agent 可闭环</li>
 *   <li>{@link #L3} — 复杂任务：多目标跨领域、多步骤依赖，需多 Agent 协同</li>
 * </ul>
 */
public enum ComplexityLevel {
    /** L1 简单：单步或少步操作，低预算。 */
    L1,
    /** L2 中等：少量工具调用与多步推理。 */
    L2,
    /** L3 复杂：多工具编排、长链路、高预算。 */
    L3
}
