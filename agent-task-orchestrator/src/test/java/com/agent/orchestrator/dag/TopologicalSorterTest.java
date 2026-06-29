package com.agent.orchestrator.dag;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("单节点 DAG 排序后应返回包含该节点的单元素列表")
    void should_ReturnSingleElementList_When_SortingSingleNodeDag() {
        List<DagNode> nodes = List.of(node("n1"));
        List<DagEdge> edges = Collections.emptyList();

        List<String> sorted = sorter.sort(nodes, edges);

        assertThat(sorted.size()).isEqualTo(1);
        assertThat(sorted.get(0)).isEqualTo("n1");
    }

    @Test
    @DisplayName("线性链 DAG 排序后应返回符合拓扑顺序的节点列表")
    void should_ReturnTopologicalOrder_When_DagIsLinearChain() {
        // n1 -> n2 -> n3
        List<DagNode> nodes = List.of(node("n1"), node("n2"), node("n3"));
        List<DagEdge> edges = List.of(edge("n1", "n2"), edge("n2", "n3"));

        List<String> sorted = sorter.sort(nodes, edges);

        assertThat(sorted.size()).isEqualTo(3);
        // n1 必须在 n2 之前，n2 必须在 n3 之前
        assertThat(sorted.indexOf("n1") < sorted.indexOf("n2")).as("n1 应在 n2 之前").isTrue();
        assertThat(sorted.indexOf("n2") < sorted.indexOf("n3")).as("n2 应在 n3 之前").isTrue();
    }

    @Test
    @DisplayName("含并行分支的 DAG 排序后应返回满足所有依赖关系的有效拓扑顺序")
    void should_ReturnValidTopologicalOrder_When_DagHasParallelBranches() {
        // n0 -> n1, n0 -> n2, n1 -> n3, n2 -> n3
        List<DagNode> nodes = List.of(node("n0"), node("n1"), node("n2"), node("n3"));
        List<DagEdge> edges = List.of(
                edge("n0", "n1"),
                edge("n0", "n2"),
                edge("n1", "n3"),
                edge("n2", "n3"));

        List<String> sorted = sorter.sort(nodes, edges);

        assertThat(sorted.size()).isEqualTo(4);
        // n0 必须在 n1/n2 之前，n1/n2 必须在 n3 之前
        assertThat(sorted.indexOf("n0") < sorted.indexOf("n1")).as("n0 应在 n1 之前").isTrue();
        assertThat(sorted.indexOf("n0") < sorted.indexOf("n2")).as("n0 应在 n2 之前").isTrue();
        assertThat(sorted.indexOf("n1") < sorted.indexOf("n3")).as("n1 应在 n3 之前").isTrue();
        assertThat(sorted.indexOf("n2") < sorted.indexOf("n3")).as("n2 应在 n3 之前").isTrue();
    }

    @Test
    @DisplayName("含环 DAG 排序时应抛出 BusinessException 且错误码为 DAG_CYCLE_DETECTED")
    void should_ThrowBusinessExceptionWithDagCycleDetected_When_DagHasCycle() {
        // n1 -> n2 -> n3 -> n1 (形成环)
        List<DagNode> nodes = List.of(node("n1"), node("n2"), node("n3"));
        List<DagEdge> edges = List.of(
                edge("n1", "n2"),
                edge("n2", "n3"),
                edge("n3", "n1"));

        assertThatThrownBy(() -> sorter.sort(nodes, edges))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DAG_CYCLE_DETECTED));
    }

    @Test
    @DisplayName("节点列表为空时排序应返回空列表")
    void should_ReturnEmptyList_When_NodesListIsEmpty() {
        List<DagNode> nodes = Collections.emptyList();
        List<DagEdge> edges = Collections.emptyList();

        List<String> sorted = sorter.sort(nodes, edges);

        assertThat(sorted).as("空节点列表应返回空排序结果").isEmpty();
    }

    @Test
    @DisplayName("NONE 类型边不构成依赖，仅 DATA/LOGIC 边计入拓扑排序")
    void should_IgnoreNoneEdgesAndOnlyCountDataAndLogicAsDependencies_When_EdgeTypeIsNone() {
        // n1 --NONE--> n2 (NONE 边不应构成依赖，n1 和 n2 可任意顺序)
        List<DagNode> nodes = List.of(node("n1"), node("n2"));
        List<DagEdge> edges = List.of(
                DagEdge.builder().dagId(1L).parentNodeId("n1").childNodeId("n2").edgeType("NONE").build());

        List<String> sorted = sorter.sort(nodes, edges);

        assertThat(sorted.size()).isEqualTo(2);
        assertThat(sorted).contains("n1");
        assertThat(sorted).contains("n2");
    }
}
