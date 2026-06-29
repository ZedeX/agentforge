package com.agent.orchestrator.validator;

import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 5 维度 DAG 校验上下文 POJO（对齐 doc 03-task-engine §3.3 Step 5 规划自检优化）。
 *
 * <p>封装 DAG 节点、边、期望 deliverables、成本预算与重试上限等校验所需输入。
 * 调用方（AiPlanner / Orchestrator）在调用 {@link PlanValidator#validate(ValidationContext)}
 * 前组装本上下文。</p>
 *
 * <p>设计说明：参考 {@code ReplanModeSelector.ReplanContext} 的 Lombok 风格
 * （@Data + @Builder + @AllArgsConstructor + @NoArgsConstructor），所有字段使用
 * {@code @Builder.Default} 提供空集合默认值，避免 NPE。</p>
 *
 * <p>字段语义：</p>
 * <ul>
 *   <li>{@link #nodes} / {@link #edges} — 复用 {@link DagNode} / {@link DagEdge} POJO</li>
 *   <li>{@link #deliverables} — 期望产出列表（完备性维度用，匹配节点 outputs JSON 字符串）</li>
 *   <li>{@link #costLimitCent} — 成本上限（分），{@code <=0} 表示不限制预算（成本维度跳过）</li>
 *   <li>{@link #estimatedCostCent} — 预估成本（分），与 costLimitCent × 80% 比较</li>
 *   <li>{@link #maxRetries} — 自检失败后最大重试轮数（默认 2，对应 doc 中"最多 2 轮"）</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationContext {

    /**
     * DAG 节点列表（复用 {@link DagNode} POJO）。
     * <p>可为空；空集合表示无节点 DAG（完备性维度将根据 deliverables 是否为空判定）。</p>
     */
    @Builder.Default
    private List<DagNode> nodes = Collections.emptyList();

    /**
     * DAG 边列表（复用 {@link DagEdge} POJO）。
     * <p>效率维度遍历此列表识别可并行但被串行的节点对。</p>
     */
    @Builder.Default
    private List<DagEdge> edges = Collections.emptyList();

    /**
     * 期望产出列表（完备性校验用）。
     * <p>每个 deliverable 字符串需能在某个节点的 outputs JSON 字符串中匹配到。
     * 空列表表示无完备性要求 → 完备性维度通过。</p>
     */
    @Builder.Default
    private List<String> deliverables = Collections.emptyList();

    /**
     * 成本上限（单位：分，1 元 = 100 分）。
     * <p>{@code <=0} 表示未设置预算 → 成本维度跳过（视为通过）。</p>
     */
    private long costLimitCent;

    /**
     * 预估成本（单位：分）。
     * <p>与 {@link #costLimitCent} × 80% 比较，超过则成本维度失败。</p>
     */
    private long estimatedCostCent;

    /**
     * 自检失败后最大重试轮数（默认 2，对应 doc 中"最多 2 轮"修正）。
     * <p>注意：本字段仅作上下文记录，实际重试循环由调用方（AiPlanner）负责；
     * PlanValidator 本身不持有重试状态，便于纯函数式测试。</p>
     */
    @Builder.Default
    private int maxRetries = 2;
}
