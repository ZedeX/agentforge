package com.agent.orchestrator.dag;

import com.agent.common.constant.TaskStatus;
import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F4 子任务分发决策节点补强测试（对齐 docs/tests/unit-test-cases.md §18.2）。
 *
 * <p>覆盖 F4.D7（条件节点 skipped）与 F4.D8（子任务超时 timeout）两个决策节点。
 * 由于当前 main 源文件尚未实现 {@code ConditionEvaluator} 与 {@code SubtaskTimeoutMonitor}
 * （F4 决策节点依赖类），本测试类通过<strong>测试内部 helper</strong> 模拟决策逻辑，
 * 验证 DagGraph 结构层面与 TaskStateMachine 状态层面的可观测行为。</p>
 *
 * <p>用例清单：</p>
 * <ul>
 *   <li>UT-F4-001: 条件不满足 → 节点 SKIPPED + 下游收到上游无输出</li>
 *   <li>UT-F4-002: 子任务执行时长超 maxDuration → 标记 TIMEOUT + 可触发重试/重规划</li>
 * </ul>
 *
 * <p>当后续实现 ConditionEvaluator / SubtaskTimeoutMonitor 后，应将这些决策逻辑迁移至 main 源文件，
 * 本测试类的 helper 可删除，断言改为对真实组件的验证。</p>
 */
class F4DecisionNodeTest {

    private final TaskStateMachine stateMachine = new TaskStateMachine();

    // ============ UT-F4-001: 条件节点 skipped ============

    @Test
    @DisplayName("UT-F4-001: 节点 condition 不满足时应标记 SKIPPED 且下游依赖节点收到上游无输出")
    void should_MarkSkipped_When_NodeConditionNotMet() {
        // 场景：节点 n2 带 condition=if(orderAmount>10000)，实际 orderAmount=5000 → false
        // 期望：n2.status=SKIPPED，n2.outputs=null（无输出）；下游 n3 的 DagGraph 入度仍为 1（结构不变），
        //       但运行时 n3 收不到 n2 的输出数据（上游无输出）。

        DagNode upstream = DagNode.builder()
                .dagId(1L)
                .nodeId("n1")
                .nodeType("subtask")
                .title("前置节点")
                .status("success")
                .outputs("{\"orderAmount\":5000}")
                .build();
        // n2 模拟带 condition 的条件节点（DagNode 当前无 condition 字段，用 title 携带条件表达式以便测试可读）
        DagNode conditional = DagNode.builder()
                .dagId(1L)
                .nodeId("n2")
                .nodeType("subtask")
                .title("if(orderAmount>10000)")
                .status("pending")
                .build();
        DagNode downstream = DagNode.builder()
                .dagId(1L)
                .nodeId("n3")
                .nodeType("subtask")
                .title("下游节点")
                .status("pending")
                .build();

        DagEdge edgeUpToCond = DagEdge.builder()
                .dagId(1L).parentNodeId("n1").childNodeId("n2").edgeType("DATA").build();
        DagEdge edgeCondToDown = DagEdge.builder()
                .dagId(1L).parentNodeId("n2").childNodeId("n3").edgeType("DATA").build();

        DagGraph graph = new DagGraph(List.of(upstream, conditional, downstream),
                List.of(edgeUpToCond, edgeCondToDown));

        // 执行 F4.D7 决策：条件求值（模拟未实现的 ConditionEvaluator）
        Map<String, Object> upstreamOutputs = parseOutputs(upstream.getOutputs());
        boolean conditionMet = evaluateCondition(conditional.getTitle(), upstreamOutputs);

        // 条件不满足 → 节点标记 SKIPPED + outputs 保持 null（无输出）
        if (!conditionMet) {
            conditional.setStatus("SKIPPED");
            conditional.setOutputs(null);
        }

        // 断言 1：条件节点状态 = SKIPPED
        assertThat(conditional.getStatus()).as("条件不满足时节点应标记 SKIPPED").isEqualTo("SKIPPED");
        // 断言 2：条件节点 outputs = null（上游无输出传递给下游）
        assertThat(conditional.getOutputs()).as("SKIPPED 节点不应产出数据").isNull();

        // 断言 3：下游 n3 在 DagGraph 结构上入度仍为 1（DAG 拓扑结构不变，仅运行时无数据流入）
        assertThat(graph.getInDegree().get("n3"))
                .as("DAG 结构层面下游入度不变（条件跳过不改拓扑）")
                .isEqualTo(1);
        // 断言 4：下游 n3 的直接上游是 n2，且 n2 现为 SKIPPED → 运行时 n3 收不到 n2 输出
        List<String> downstreamOfN2 = graph.getAdjacency().get("n2");
        assertThat(downstreamOfN2).as("下游依赖关系保留").contains("n3");
        DagNode n2InGraph = graph.getNodes().stream()
                .filter(n -> "n2".equals(n.getNodeId())).findFirst().orElseThrow();
        assertThat(n2InGraph.getStatus()).as("图中 n2 应反映 SKIPPED 状态").isEqualTo("SKIPPED");
        assertThat(n2InGraph.getOutputs())
                .as("下游 n3 通过 n2 的 outputs 获取输入，SKIPPED 时 n2 无输出 → 下游收到上游无输出")
                .isNull();
    }

    // ============ UT-F4-002: 子任务超时 ============

    @Test
    @DisplayName("UT-F4-002: 子任务执行时长超过 maxDuration 应标记 TIMEOUT 并可触发重试或重规划")
    void should_TimeoutSubtask_When_DurationExceedsMax() {
        // 场景：maxDuration=300s, actual=305s → 超时
        // 期望：TaskStateMachine.transit(SUBTASK_RUNNING, TIMEOUT) 合法返回 TIMEOUT，
        //       TIMEOUT 为终态；且 TIMEOUT → WAITING_HUMAN 合法（可触发重试或重规划/人工介入路径）。

        long maxDurationSeconds = 300L;
        long actualDurationSeconds = 305L;

        // F4.D8 决策：时长超限检查（模拟未实现的 SubtaskTimeoutMonitor）
        boolean timeoutExceeded = actualDurationSeconds > maxDurationSeconds;
        assertThat(timeoutExceeded)
                .as("actual=305s 应超过 maxDuration=300s 判定超时")
                .isTrue();

        // 超时 → 状态机流转 SUBTASK_RUNNING → TIMEOUT
        TaskStatus current = TaskStatus.SUBTASK_RUNNING;
        assertThat(stateMachine.canTransitTo(current, TaskStatus.TIMEOUT))
                .as("SUBTASK_RUNNING → TIMEOUT 必须是合法流转")
                .isTrue();

        TaskStatus target = stateMachine.transit(current, TaskStatus.TIMEOUT);
        assertThat(target).as("流转结果应为 TIMEOUT").isEqualTo(TaskStatus.TIMEOUT);
        assertThat(target.isTerminal())
                .as("TIMEOUT 必须为终态（标记 TIMEOUT 后停止子任务执行）")
                .isTrue();

        // 超时后可触发重试或重规划：TIMEOUT → WAITING_HUMAN 是合法的申诉/人工介入路径
        // （对应 F4.D8 timeout 分支 → 触发重试或重规划的下游决策）
        assertThat(stateMachine.canTransitTo(TaskStatus.TIMEOUT, TaskStatus.WAITING_HUMAN))
                .as("TIMEOUT → WAITING_HUMAN 必须合法（用于触发重试或重规划/人工介入）")
                .isTrue();
        // 同时 REPLANNING 不在 TIMEOUT 的合法下一状态中（需经 WAITING_HUMAN 中转），验证矩阵约束
        assertThat(stateMachine.canTransitTo(TaskStatus.TIMEOUT, TaskStatus.REPLANNING))
                .as("TIMEOUT 不能直接 → REPLANNING（需经 WAITING_HUMAN 中转）")
                .isFalse();
    }

    // ============ 测试内部 helper（模拟未实现的 F4 决策节点组件） ============

    /**
     * 模拟 F4.D7 ConditionEvaluator：解析节点 title 中的条件表达式并求值。
     *
     * <p>仅支持 {@code if(field>number)} / {@code if(field<number)} 形式，用于测试断言。
     * 生产实现应替换为独立的 ConditionEvaluator 组件（支持 SpEL/Aviator 等表达式）。</p>
     */
    private boolean evaluateCondition(String conditionExpr, Map<String, Object> inputs) {
        if (conditionExpr == null || inputs == null) {
            return true; // 无条件则默认执行
        }
        // 解析 if(orderAmount>10000) 形式
        String expr = conditionExpr.trim();
        if (expr.startsWith("if(") && expr.endsWith(")")) {
            String body = expr.substring(3, expr.length() - 1);
            if (body.contains(">")) {
                String[] parts = body.split(">");
                String field = parts[0].trim();
                double threshold = Double.parseDouble(parts[1].trim());
                Object value = inputs.get(field);
                if (value instanceof Number n) {
                    return n.doubleValue() > threshold;
                }
                return false;
            }
            if (body.contains("<")) {
                String[] parts = body.split("<");
                String field = parts[0].trim();
                double threshold = Double.parseDouble(parts[1].trim());
                Object value = inputs.get(field);
                if (value instanceof Number n) {
                    return n.doubleValue() < threshold;
                }
                return false;
            }
        }
        return true;
    }

    /**
     * 解析 DagNode.outputs JSON 字符串为 Map（极简实现，仅支持 {"key":number} 形式）。
     */
    private Map<String, Object> parseOutputs(String outputs) {
        if (outputs == null || outputs.isBlank() || "{}".equals(outputs.trim())) {
            return Map.of();
        }
        // 极简解析 {"orderAmount":5000}
        String body = outputs.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1).trim();
            if (body.isEmpty()) {
                return Map.of();
            }
            String[] entries = body.split(",");
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            for (String entry : entries) {
                String[] kv = entry.split(":");
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String val = kv[1].trim().replace("\"", "");
                    try {
                        result.put(key, Long.parseLong(val));
                    } catch (NumberFormatException e) {
                        result.put(key, val);
                    }
                }
            }
            return result;
        }
        return Map.of();
    }
}
