package com.agent.orchestrator.dag;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TopologicalSorter 单元测试（Red 阶段：实现尚未存在，预期编译失败）。
 *
 * <p>对齐 doc 03-task-engine §4.2 Kahn 拓扑排序算法：
 * 仅 DATA / LOGIC 边构成依赖（NONE 不构成），按入度递减划分层级。
 * 检测到环抛 {@link BusinessException}({@link ErrorCode#DAG_CYCLE_DETECTED})。</p>
 *
 * <p>测试覆盖 4 类 DAG 形态：单节点 / 线性链 / 并行分支 / 含环。</p>
 */
class TopologicalSorterTest {

    private final TopologicalSorter sorter = new TopologicalSorter();

    private DagNode node(String nodeId) {
        return DagNode.builder().dagId(1L).nodeId(nodeId).nodeType("subtask").title("node-" + nodeId).build();
    }

    private DagEdge edge(String from, String to) {
        return DagEdge.builder().dagId(1L).parentNodeId(from).childNodeId(to).edgeType("LOGIC").build();
    }

    @Test
    void sort_singleNode_returnsSingleElementList() {
        List<DagNode> nodes = List.of(node("n1"));
        List<DagEdge> edges = Collections.emptyList();

        List<String> sorted = sorter.sort(nodes, edges);

        assertEquals(1, sorted.size());
        assertEquals("n1", sorted.get(0));
    }

    @Test
    void sort_linearChain_returnsTopologicalOrder() {
        // n1 -> n2 -> n3
        List<DagNode> nodes = List.of(node("n1"), node("n2"), node("n3"));
        List<DagEdge> edges = List.of(edge("n1", "n2"), edge("n2", "n3"));

        List<String> sorted = sorter.sort(nodes, edges);

        assertEquals(3, sorted.size());
        // n1 必须在 n2 之前，n2 必须在 n3 之前
        assertTrue(sorted.indexOf("n1") < sorted.indexOf("n2"), "n1 应在 n2 之前");
        assertTrue(sorted.indexOf("n2") < sorted.indexOf("n3"), "n2 应在 n3 之前");
    }

    @Test
    void sort_parallelBranches_returnsValidTopologicalOrder() {
        // n0 -> n1, n0 -> n2, n1 -> n3, n2 -> n3
        List<DagNode> nodes = List.of(node("n0"), node("n1"), node("n2"), node("n3"));
        List<DagEdge> edges = List.of(
                edge("n0", "n1"),
                edge("n0", "n2"),
                edge("n1", "n3"),
                edge("n2", "n3"));

        List<String> sorted = sorter.sort(nodes, edges);

        assertEquals(4, sorted.size());
        // n0 必须在 n1/n2 之前，n1/n2 必须在 n3 之前
        assertTrue(sorted.indexOf("n0") < sorted.indexOf("n1"), "n0 应在 n1 之前");
        assertTrue(sorted.indexOf("n0") < sorted.indexOf("n2"), "n0 应在 n2 之前");
        assertTrue(sorted.indexOf("n1") < sorted.indexOf("n3"), "n1 应在 n3 之前");
        assertTrue(sorted.indexOf("n2") < sorted.indexOf("n3"), "n2 应在 n3 之前");
    }

    @Test
    void sort_cycleDetected_throwsBusinessExceptionWithDagCycleDetectedCode() {
        // n1 -> n2 -> n3 -> n1 (形成环)
        List<DagNode> nodes = List.of(node("n1"), node("n2"), node("n3"));
        List<DagEdge> edges = List.of(
                edge("n1", "n2"),
                edge("n2", "n3"),
                edge("n3", "n1"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> sorter.sort(nodes, edges),
                "检测到环应抛 BusinessException");
        assertEquals(ErrorCode.DAG_CYCLE_DETECTED, ex.getErrorCode(),
                "错误码应为 DAG_CYCLE_DETECTED");
    }

    @Test
    void sort_emptyNodes_returnsEmptyList() {
        List<DagNode> nodes = Collections.emptyList();
        List<DagEdge> edges = Collections.emptyList();

        List<String> sorted = sorter.sort(nodes, edges);

        assertTrue(sorted.isEmpty(), "空节点列表应返回空排序结果");
    }

    @Test
    void sort_noneEdgesIgnored_onlyDataAndLogicCountAsDependencies() {
        // n1 --NONE--> n2 (NONE 边不应构成依赖，n1 和 n2 可任意顺序)
        List<DagNode> nodes = List.of(node("n1"), node("n2"));
        List<DagEdge> edges = List.of(
                DagEdge.builder().dagId(1L).parentNodeId("n1").childNodeId("n2").edgeType("NONE").build());

        List<String> sorted = sorter.sort(nodes, edges);

        assertEquals(2, sorted.size());
        assertTrue(sorted.contains("n1"));
        assertTrue(sorted.contains("n2"));
    }
}
