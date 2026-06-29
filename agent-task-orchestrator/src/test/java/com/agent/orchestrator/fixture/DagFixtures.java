package com.agent.orchestrator.fixture;

import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;

import java.util.Arrays;
import java.util.List;

import static com.agent.orchestrator.fixture.TestConstants.*;

/**
 * DagNode / DagEdge 测试公共 Fixture 工厂（FIX-01 整改，对齐 FN-011）。
 *
 * <p>集中管理 DAG 节点与边的默认值构造逻辑，覆盖：</p>
 * <ul>
 *   <li>3 种节点类型：{@code ATOMIC}（原子）/ {@code COMPOSITE}（组合）/ {@code DECISION}（决策）；</li>
 *   <li>3 种边类型：{@code DATA}（数据依赖）/ {@code LOGIC}（逻辑依赖）/ {@code NONE}（无依赖）；</li>
 *   <li>复杂 DAG：由多个节点 + 边组合而成，用于 {@code DagValidatorTest} / {@code TopologicalSorterTest}。</li>
 * </ul>
 *
 * <p>设计原则：</p>
 * <ol>
 *   <li>工厂方法以 {@code buildXxx} 命名（FIX-02）；</li>
 *   <li>复杂 DAG 由基础节点 + 边组合而成（FIX-03）：
 *       {@link #buildComplexDag()} 内部调用 {@link #buildAtomicNode(String)} 等基础工厂；</li>
 *   <li>不依赖 Spring Context，纯静态工厂。</li>
 * </ol>
 *
 * @see TaskFixtures
 * @see EventFixtures
 * @see TestConstants
 */
public final class DagFixtures {

    /** 默认节点状态：pending（对齐 DagNode.prePersist 默认值）。 */
    public static final String NODE_STATUS_PENDING = "pending";
    /** 默认节点状态：running。 */
    public static final String NODE_STATUS_RUNNING = "running";
    /** 默认节点状态：success。 */
    public static final String NODE_STATUS_SUCCESS = "success";

    private DagFixtures() {
        // 工具类禁止实例化
    }

    // ============ 基础 DagNode Fixture ============

    /**
     * 构造一个最简单的 DagNode：ATOMIC 类型、pending 状态、仅必填字段非空。
     *
     * <p>所有 buildXxxNode 工厂方法均以此为基础组合而成（FIX-03）。</p>
     *
     * @param nodeId 节点 ID
     * @return 已填充默认值的 DagNode
     */
    public static DagNode buildSimpleDagNode(String nodeId) {
        return DagNode.builder()
                .dagId(DEFAULT_DAG_ID)
                .nodeId(nodeId)
                .nodeType(NODE_TYPE_ATOMIC)
                .title("测试节点 " + nodeId)
                .agentId(DEFAULT_AGENT_ID)
                .abilityTags("[]")
                .inputs("{}")
                .outputs("{}")
                .status(NODE_STATUS_PENDING)
                .build();
    }

    /**
     * 构造一个 ATOMIC 类型节点（原子任务，最小执行单元）。
     *
     * @param nodeId 节点 ID
     * @return ATOMIC 类型的 DagNode
     */
    public static DagNode buildAtomicNode(String nodeId) {
        return buildSimpleDagNode(nodeId);
    }

    /**
     * 构造一个 COMPOSITE 类型节点（组合任务，含子 DAG）。
     *
     * @param nodeId 节点 ID
     * @return COMPOSITE 类型的 DagNode
     */
    public static DagNode buildCompositeNode(String nodeId) {
        DagNode node = buildSimpleDagNode(nodeId);
        node.setNodeType(NODE_TYPE_COMPOSITE);
        node.setSubtaskId(DEFAULT_SUBTASK_ID);
        return node;
    }

    /**
     * 构造一个 DECISION 类型节点（决策节点，对应 F4/F5）。
     *
     * @param nodeId 节点 ID
     * @return DECISION 类型的 DagNode
     */
    public static DagNode buildDecisionNode(String nodeId) {
        DagNode node = buildSimpleDagNode(nodeId);
        node.setNodeType(NODE_TYPE_DECISION);
        node.setInputs("{\"condition\":\"status==FAILED\",\"maxRetries\":3}");
        return node;
    }

    /**
     * 构造一个 running 状态的 DagNode。
     *
     * @param nodeId 节点 ID
     * @return running 状态的 DagNode
     */
    public static DagNode buildRunningDagNode(String nodeId) {
        DagNode node = buildSimpleDagNode(nodeId);
        node.setStatus(NODE_STATUS_RUNNING);
        return node;
    }

    /**
     * 构造一个 success 状态的 DagNode。
     *
     * @param nodeId 节点 ID
     * @return success 状态的 DagNode
     */
    public static DagNode buildSuccessDagNode(String nodeId) {
        DagNode node = buildSimpleDagNode(nodeId);
        node.setStatus(NODE_STATUS_SUCCESS);
        node.setOutputs("{\"result\":\"done\"}");
        return node;
    }

    // ============ 基础 DagEdge Fixture ============

    /**
     * 构造一个 DATA 类型的 DagEdge（数据依赖，最常用）。
     *
     * @param parentNodeId 父节点 ID
     * @param childNodeId  子节点 ID
     * @return DATA 类型的 DagEdge
     */
    public static DagEdge buildDagEdge(String parentNodeId, String childNodeId) {
        return DagEdge.builder()
                .dagId(DEFAULT_DAG_ID)
                .parentNodeId(parentNodeId)
                .childNodeId(childNodeId)
                .edgeType(EDGE_TYPE_DATA)
                .paramMapping("{}")
                .build();
    }

    /**
     * 构造一个 DATA 类型的 DagEdge（数据依赖）。
     *
     * @param parentNodeId 父节点 ID
     * @param childNodeId  子节点 ID
     * @return DATA 类型的 DagEdge
     */
    public static DagEdge buildDataEdge(String parentNodeId, String childNodeId) {
        return buildDagEdge(parentNodeId, childNodeId);
    }

    /**
     * 构造一个 LOGIC 类型的 DagEdge（逻辑依赖，如条件分支）。
     *
     * @param parentNodeId 父节点 ID
     * @param childNodeId  子节点 ID
     * @return LOGIC 类型的 DagEdge
     */
    public static DagEdge buildLogicEdge(String parentNodeId, String childNodeId) {
        DagEdge edge = buildDagEdge(parentNodeId, childNodeId);
        edge.setEdgeType(EDGE_TYPE_LOGIC);
        edge.setParamMapping("{\"condition\":\"status==SUCCESS\"}");
        return edge;
    }

    /**
     * 构造一个 NONE 类型的 DagEdge（无依赖，仅并行批次标记）。
     *
     * @param parentNodeId 父节点 ID
     * @param childNodeId  子节点 ID
     * @return NONE 类型的 DagEdge
     */
    public static DagEdge buildNoneEdge(String parentNodeId, String childNodeId) {
        DagEdge edge = buildDagEdge(parentNodeId, childNodeId);
        edge.setEdgeType(EDGE_TYPE_NONE);
        return edge;
    }

    // ============ 复杂 DAG Fixture（FIX-03 组合示例） ============

    /**
     * 构造一个复杂的 DAG 节点列表：3 个节点（ATOMIC + COMPOSITE + DECISION），
     * 用于 {@code DagValidatorTest} / {@code TopologicalSorterTest} 等场景。
     *
     * <p>组合关系：调用 {@link #buildAtomicNode(String)} / {@link #buildCompositeNode(String)}
     * / {@link #buildDecisionNode(String)} 三个基础工厂。</p>
     *
     * @return 3 个节点的列表
     */
    public static List<DagNode> buildComplexDagNodes() {
        return Arrays.asList(
                buildAtomicNode("n_1"),
                buildCompositeNode("n_2"),
                buildDecisionNode("n_3")
        );
    }

    /**
     * 构造一个复杂的 DAG 边列表：3 条边（DATA + LOGIC + NONE），
     * 形成 n_1 → n_2 → n_3 的链式结构。
     *
     * <p>组合关系：调用 {@link #buildDataEdge(String, String)} /
     * {@link #buildLogicEdge(String, String)} / {@link #buildNoneEdge(String, String)}。</p>
     *
     * @return 3 条边的列表
     */
    public static List<DagEdge> buildComplexDagEdges() {
        return Arrays.asList(
                buildDataEdge("n_1", "n_2"),
                buildLogicEdge("n_2", "n_3"),
                buildNoneEdge("n_1", "n_3")
        );
    }

    /**
     * 构造一个含环的 DAG 节点 + 边列表（n_1 → n_2 → n_1），用于 DAG 校验失败场景。
     *
     * @return 含环的节点 + 边列表
     */
    public static List<DagEdge> buildCyclicDagEdges() {
        return Arrays.asList(
                buildDataEdge("n_1", "n_2"),
                buildDataEdge("n_2", "n_1")
        );
    }
}
