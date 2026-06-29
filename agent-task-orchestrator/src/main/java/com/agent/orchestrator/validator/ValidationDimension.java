package com.agent.orchestrator.validator;

/**
 * 5 维度 DAG 校验维度枚举（对齐 doc 03-task-engine §3.3 Step 5 规划自检优化）。
 *
 * <p>对应 PRD「完备性、原子性、效率、成本、容错五个维度」，每个维度失败对应一种修正动作
 * （补节点 / 拆分节点 / 调 depType 为 none / 削减节点 / 注入容错配置）。</p>
 *
 * <p>取值顺序与 {@code PlanValidator.validate()} 内部执行顺序一致：</p>
 * <ol>
 *   <li>{@link #COMPLETENESS} — 完备性：所有 deliverables 都有对应产出节点</li>
 *   <li>{@link #ATOMICITY} — 原子性：单节点不混合多个职责</li>
 *   <li>{@link #EFFICIENCY} — 效率：避免可并行节点被串行</li>
 *   <li>{@link #COST} — 成本：预估 Token 不超 cost_limit_cent × 80%</li>
 *   <li>{@link #FAULT_TOLERANCE} — 容错：R3 写操作节点配置 maxRetries/undoAction</li>
 * </ol>
 */
public enum ValidationDimension {
    /** 完备性：所有 deliverables 都有对应产出节点 */
    COMPLETENESS,
    /** 原子性：单节点不混合多个职责 */
    ATOMICITY,
    /** 效率：避免可并行节点被串行 */
    EFFICIENCY,
    /** 成本：预估 Token 不超 cost_limit_cent × 80% */
    COST,
    /** 容错：R3 写操作节点配置 maxRetries/undoAction */
    FAULT_TOLERANCE
}
