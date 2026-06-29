package com.agent.orchestrator.dispatcher;

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
 * BatchPartitioner 单元测试（对齐 doc 03-task-engine §4.2 + UT-ORCH-006）。
 *
 * <p>验证基于拓扑层级的并行批次划分算法：</p>
 * <ul>
 *   <li>同层无依赖节点归为同批次</li>
 *   <li>NONE 边不构成依赖</li>
 *   <li>含环 DAG 抛 {@link ErrorCode#DAG_CYCLE_DETECTED}</li>
 * </ul>
 */
class BatchPartitionerTest {

    private final BatchPartitioner partitioner = new BatchPartitioner();

    private DagNode node(String nodeId) {
        return DagNode.builder()
                .dagId(1L)
                .nodeId(nodeId)
                .nodeType("subtask")
                .title("node-" + nodeId)
                .build();
    }

    private DagEdge edge(String from, String to) {
        return DagEdge.builder()
                .dagId(1L)
                .parentNodeId(from)
                .childNodeId(to)
                .edgeType("LOGIC")
                .build();
    }

    @Test
    @DisplayName("UT-ORCH-006: 含并行根节点的 DAG 应划分为两个批次：[n1,n2,n3] 与 [n4]")
    void should_PartitionIntoTwoBatches_When_DagHasParallelNodes() {
        // n1, n2, n3 三个无依赖根节点，全部指向 n4
        List<DagNode> nodes = List.of(node("n1"), node("n2"), node("n3"), node("n4"));
        List<DagEdge> edges = List.of(
                edge("n1", "n4"),
                edge("n2", "n4"),
                edge("n3", "n4"));

        List<Batch> batches = partitioner.partition(nodes, edges);

        assertThat(batches).hasSize(2);
        assertThat(batches.get(0).getNodeIds())
                .as("首批应包含 3 个并行根节点")
                .containsExactlyInAnyOrder("n1", "n2", "n3");
        assertThat(batches.get(1).getNodeIds())
                .as("次批应仅包含汇聚节点 n4")
                .containsExactlyInAnyOrder("n4");
        assertThat(batches.get(0).getBatchIndex()).isEqualTo(0);
        assertThat(batches.get(1).getBatchIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("单节点 DAG 应划分为单批次")
    void should_ReturnSingleBatch_When_SortingSingleNodeDag() {
        List<DagNode> nodes = List.of(node("n1"));
        List<DagEdge> edges = Collections.emptyList();

        List<Batch> batches = partitioner.partition(nodes, edges);

        assertThat(batches).hasSize(1);
        assertThat(batches.get(0).getNodeIds()).containsExactly("n1");
        assertThat(batches.get(0).getBatchIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("线性链 DAG 应按拓扑层划分为每节点一批次")
    void should_ReturnOneBatchPerNode_When_DagIsLinearChain() {
        // n1 -> n2 -> n3
        List<DagNode> nodes = List.of(node("n1"), node("n2"), node("n3"));
        List<DagEdge> edges = List.of(edge("n1", "n2"), edge("n2", "n3"));

        List<Batch> batches = partitioner.partition(nodes, edges);

        assertThat(batches).hasSize(3);
        assertThat(batches.get(0).getNodeIds()).containsExactly("n1");
        assertThat(batches.get(1).getNodeIds()).containsExactly("n2");
        assertThat(batches.get(2).getNodeIds()).containsExactly("n3");
    }

    @Test
    @DisplayName("空 DAG 应返回空批次列表")
    void should_ReturnEmptyList_When_DagIsEmpty() {
        List<Batch> batches = partitioner.partition(Collections.emptyList(), Collections.emptyList());

        assertThat(batches).as("空 DAG 应返回空批次列表").isEmpty();
    }

    @Test
    @DisplayName("NONE 类型边不构成依赖，两端节点应归为同批次")
    void should_GroupNodesIntoSameBatch_When_EdgeTypeIsNone() {
        // n1 --NONE--> n2，NONE 边不应构成依赖，n1 和 n2 同批次
        List<DagNode> nodes = List.of(node("n1"), node("n2"));
        List<DagEdge> edges = List.of(
                DagEdge.builder()
                        .dagId(1L)
                        .parentNodeId("n1")
                        .childNodeId("n2")
                        .edgeType("NONE")
                        .build());

        List<Batch> batches = partitioner.partition(nodes, edges);

        assertThat(batches).hasSize(1);
        assertThat(batches.get(0).getNodeIds()).containsExactlyInAnyOrder("n1", "n2");
    }

    @Test
    @DisplayName("DATA 类型边构成依赖，应跨批次")
    void should_PartitionAcrossBatches_When_EdgeTypeIsData() {
        // n1 --DATA--> n2，DATA 边构成依赖，应跨批次
        List<DagNode> nodes = List.of(node("n1"), node("n2"));
        List<DagEdge> edges = List.of(
                DagEdge.builder()
                        .dagId(1L)
                        .parentNodeId("n1")
                        .childNodeId("n2")
                        .edgeType("DATA")
                        .build());

        List<Batch> batches = partitioner.partition(nodes, edges);

        assertThat(batches).hasSize(2);
        assertThat(batches.get(0).getNodeIds()).containsExactly("n1");
        assertThat(batches.get(1).getNodeIds()).containsExactly("n2");
    }

    @Test
    @DisplayName("菱形 DAG（n1→n2,n1→n3,n2→n4,n3→n4）应划分为三个批次")
    void should_ReturnThreeBatches_When_DagIsDiamond() {
        List<DagNode> nodes = List.of(node("n1"), node("n2"), node("n3"), node("n4"));
        List<DagEdge> edges = List.of(
                edge("n1", "n2"),
                edge("n1", "n3"),
                edge("n2", "n4"),
                edge("n3", "n4"));

        List<Batch> batches = partitioner.partition(nodes, edges);

        assertThat(batches).hasSize(3);
        assertThat(batches.get(0).getNodeIds()).containsExactly("n1");
        assertThat(batches.get(1).getNodeIds()).containsExactlyInAnyOrder("n2", "n3");
        assertThat(batches.get(2).getNodeIds()).containsExactly("n4");
    }

    @Test
    @DisplayName("含环 DAG 划分时应抛出 BusinessException 且错误码为 DAG_CYCLE_DETECTED")
    void should_ThrowBusinessExceptionWithDagCycleDetected_When_DagHasCycle() {
        // n1 -> n2 -> n3 -> n1 (形成环)
        List<DagNode> nodes = List.of(node("n1"), node("n2"), node("n3"));
        List<DagEdge> edges = List.of(
                edge("n1", "n2"),
                edge("n2", "n3"),
                edge("n3", "n1"));

        assertThatThrownBy(() -> partitioner.partition(nodes, edges))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DAG_CYCLE_DETECTED));
    }

    @Test
    @DisplayName("Batch POJO 应正确暴露 batchIndex 与节点列表")
    void should_ExposeBatchIndexAndNodes_When_BatchCreated() {
        DagNode n1 = node("n1");
        DagNode n2 = node("n2");

        Batch batch = Batch.builder()
                .batchIndex(2)
                .nodes(List.of(n1, n2))
                .build();

        assertThat(batch.getBatchIndex()).isEqualTo(2);
        assertThat(batch.getNodes()).containsExactly(n1, n2);
        assertThat(batch.getNodeIds()).containsExactly("n1", "n2");
    }

    @Test
    @DisplayName("Batch.getNodeIds 在节点列表为空时返回空列表")
    void should_ReturnEmptyList_When_BatchHasNoNodes() {
        Batch emptyBatch = Batch.builder().batchIndex(0).nodes(Collections.emptyList()).build();

        assertThat(emptyBatch.getNodeIds()).isEmpty();
    }
}
