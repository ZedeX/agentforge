package com.agent.orchestrator.replanner;

/**
 * 重规划模式枚举（对齐 doc 03-task-engine §5.2 + F5 决策流程）。
 *
 * <ul>
 *   <li>{@link #INCREMENTAL} - 增量重规划：保留已成功节点（frozenNodes），仅重新规划
 *       受影响部分（失败节点 + 其下游）。默认优先采用。</li>
 *   <li>{@link #FULL} - 全量重规划：重新生成整个 DAG（version+1，旧版本保留审计）。
 *       仅在极端场景触发（用户需求变更、增量失败 2 次、失败过半判定为致命异常）。</li>
 *   <li>{@link #ABORT} - 终止：重规划次数耗尽（replan_count &gt; max_replan），
 *       任务转 {@code WAITING_HUMAN} 由人工介入兜底。</li>
 * </ul>
 *
 * <p>本枚举对应 doc 03 §8.3 共享枚举 {@code com.agentplatform.task.common.enums.ReplanMode}，
 * 新增 {@code ABORT} 表项以承载"熔断后终止重规划"的语义（避免抛异常与正常返回混淆）。</p>
 */
public enum ReplanMode {
    /**
     * 增量重规划：保留已成功节点，仅重新规划失败节点及其下游。
     */
    INCREMENTAL,

    /**
     * 全量重规划：重新生成整个 DAG，version+1。
     */
    FULL,

    /**
     * 终止重规划：重规划次数耗尽，转人工介入。
     */
    ABORT
}
