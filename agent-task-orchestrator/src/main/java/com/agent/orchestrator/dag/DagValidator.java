package com.agent.orchestrator.dag;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DAG 结构合法性校验器，对齐 doc 03-task-engine §4.3 + T3.3 五维校验。
 *
 * <p>五个校验维度（任一失败即抛 {@link BusinessException}）：</p>
 * <ol>
 *   <li>节点非空：nodes 不能为空列表</li>
 *   <li>subtask_id 唯一：subtask 节点的 subtaskId 不能重复</li>
 *   <li>入度出度合法：每条边引用的 parent / child 节点必须存在</li>
 *   <li>无孤立节点：subtask 节点必须有入边或出边（start/end 不受限）</li>
 *   <li>无环：DAG 不允许存在环</li>
 * </ol>
 *
 * <p>错误码映射：维度 1-4 失败返回 {@link ErrorCode#PARAM_INVALID}，
 * 维度 5 失败返回 {@link ErrorCode#DAG_CYCLE_DETECTED}。</p>
 *
 * <p>实现说明：通过组合 {@link DagGraph} + {@link TopologicalSorter} 完成 5 维校验。
 * 综合校验入口构建一次 {@link DagGraph}，复用其邻接表/入度表给各维度校验方法，
 * 避免每个维度重复扫描节点/边集合。</p>
 */
public class DagValidator {

    private final TopologicalSorter sorter = new TopologicalSorter();

    /**
     * 综合校验：依次执行 5 维校验，任一失败即抛异常。
     *
     * @param nodes DAG 节点列表
     * @param edges DAG 边列表
     * @throws BusinessException 校验失败时抛出，错误码见类 Javadoc
     */
    public void validate(List<DagNode> nodes, List<DagEdge> edges) {
        validateNodesNotEmpty(nodes);
        DagGraph graph = new DagGraph(nodes, edges);
        validateSubtaskIdUnique(nodes);
        validateEdgeReferences(graph, edges);
        validateNoOrphanSubtaskNodes(nodes, edges);
        validateNoCycle(graph);
    }

    /** 维度 1：节点非空。 */
    private void validateNodesNotEmpty(List<DagNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "DAG 节点列表不能为空");
        }
    }

    /** 维度 2：subtask_id 唯一性（仅校验非空 subtaskId）。 */
    private void validateSubtaskIdUnique(List<DagNode> nodes) {
        Set<String> seen = new HashSet<>();
        for (DagNode node : nodes) {
            String subtaskId = node.getSubtaskId();
            if (subtaskId == null) {
                continue; // start/end 节点无 subtaskId，跳过
            }
            if (!seen.add(subtaskId)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "DAG subtask_id 重复: " + subtaskId);
            }
        }
    }

    /** 维度 3：入度出度合法性 - 每条边引用的 parent/child 节点必须存在（基于 DagGraph 节点集合）。 */
    private void validateEdgeReferences(DagGraph graph, List<DagEdge> edges) {
        Set<String> nodeIds = graph.getNodeIds();
        for (DagEdge edge : edges) {
            String from = edge.getParentNodeId();
            String to = edge.getChildNodeId();
            if (!nodeIds.contains(from)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "DAG 边引用不存在的源节点: " + from);
            }
            if (!nodeIds.contains(to)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "DAG 边引用不存在的目标节点: " + to);
            }
        }
    }

    /** 维度 4：无孤立节点 - subtask 节点必须有入边或出边。 */
    private void validateNoOrphanSubtaskNodes(List<DagNode> nodes, List<DagEdge> edges) {
        Set<String> referenced = new HashSet<>();
        for (DagEdge edge : edges) {
            referenced.add(edge.getParentNodeId());
            referenced.add(edge.getChildNodeId());
        }
        for (DagNode node : nodes) {
            if (!"subtask".equals(node.getNodeType())) {
                continue; // start/end/human_review 节点不强制要求边
            }
            if (!referenced.contains(node.getNodeId())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "DAG 存在孤立 subtask 节点: " + node.getNodeId());
            }
        }
    }

    /** 维度 5：无环 - 委托 {@link TopologicalSorter#sort(DagGraph)} 检测，环抛 DAG_CYCLE_DETECTED。 */
    private void validateNoCycle(DagGraph graph) {
        sorter.sort(graph);
    }
}
