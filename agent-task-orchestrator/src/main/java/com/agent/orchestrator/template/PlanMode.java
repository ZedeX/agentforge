package com.agent.orchestrator.template;

/**
 * 规划模式枚举（对齐 doc 03-task-engine §3.2 模板分支/智能分支 + §8.3 DagSource）。
 *
 * <ul>
 *   <li>{@link #TEMPLATE} - 模板匹配：命中预置模板，复用模板 DAG（F3 模板分支）。</li>
 *   <li>{@link #AI} - 智能规划：无模板匹配，调用 model-gateway.Chat 生成 DAG（F3 智能分支）。</li>
 * </ul>
 *
 * <p>本枚举承载"match 返回值 → mode"映射逻辑，便于上层状态机决策与测试断言。
 * 对应 doc 03 §8.3 共享枚举 {@code com.agentplatform.task.common.enums.DagSource}
 * （为避免与未来跨模块共享枚举重名冲突，本模块暂以 {@code PlanMode} 命名）。</p>
 */
public enum PlanMode {

    /** 模板匹配：复用预置 DAG 模板。 */
    TEMPLATE,

    /** 智能规划：调用大模型生成 DAG。 */
    AI;

    /**
     * 根据模板匹配结果推断规划模式。
     *
     * @param matched 匹配到的模板，null 表示无匹配
     * @return {@link #TEMPLATE} 当模板非空；否则 {@link #AI}
     */
    public static PlanMode fromMatched(TaskTemplate matched) {
        return matched != null ? TEMPLATE : AI;
    }
}
