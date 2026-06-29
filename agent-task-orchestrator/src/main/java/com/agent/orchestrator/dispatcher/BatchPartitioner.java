package com.agent.orchestrator.dispatcher;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.dag.DagGraph;
import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 批次划分器：基于 Kahn 算法按拓扑层级划分并行批次（对齐 doc 03-task-engine §4.2）。
 *
 * <p>核心规则：</p>
 * <ul>
 *   <li>同层无依赖节点归为同批次（同批次内节点可并行执行）</li>
 *   <li>仅 DATA / LOGIC 边构成依赖（NONE 不构成）</li>
 *   <li>当前批次全部 success 后才能推进下一批次</li>
 *   <li>检测到环（输出节点数少于总数）抛 {@link ErrorCode#DAG_CYCLE_DETECTED}</li>
 * </ul>
 *
 * <p>算法与 {@link com.agent.orchestrator.dag.TopologicalSorter} 一致使用 Kahn 入度递减策略，
 * 区别在于本类按"层级"输出（每个 while 循环处理一个完整层级），后者按"逐个出队"输出。</p>
 */
public class BatchPartitioner {

    /**
     * 对节点与边构成的 DAG 执行批次划分。
     *
     * @param nodes DAG 节点列表（不可为 null，可为空）
     * @param edges DAG 边列表（不可为 null，可为空）
     * @return 批次列表（按拓扑层级升序，第 0 批为根节点集合）
     * @throws BusinessException 当检测到环时，错误码为 {@link ErrorCode#DAG_CYCLE_DETECTED}
     */
    public List<Batch> partition(List<DagNode> nodes, List<DagEdge> edges) {
        return partition(new DagGraph(nodes, edges));
    }

    /**
     * 对 {@link DagGraph} 执行批次划分。
     *
     * @param graph 已构建好的 DAG 图值对象
     * @return 批次列表（按拓扑层级升序）
     * @throws BusinessException 当检测到环时，错误码为 {@link ErrorCode#DAG_CYCLE_DETECTED}
     */
    public List<Batch> partition(DagGraph graph) {
        if (graph.getNodeIds().isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<String>> adjacency = graph.getAdjacency();
        // 拷贝入度表，避免修改原图状态（Kahn 算法会递减入度）
        Map<String, Integer> inDegree = new HashMap<>(graph.getInDegree());
        // 节点 ID -> DagNode 映射
        Map<String, DagNode> nodeMap = new HashMap<>();
        for (DagNode node : graph.getNodes()) {
            nodeMap.put(node.getNodeId(), node);
        }

        List<Batch> batches = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        // 当前层级：所有入度为 0 的节点（即并行批次根集）
        List<String> currentLayer = new ArrayList<>(graph.getZeroInDegreeNodeIds());

        int batchIndex = 0;
        while (!currentLayer.isEmpty()) {
            // 1. 构造当前批次（按 currentLayer 顺序保留 DagNode）
            List<DagNode> batchNodes = new ArrayList<>(currentLayer.size());
            for (String nodeId : currentLayer) {
                DagNode node = nodeMap.get(nodeId);
                if (node != null) {
                    batchNodes.add(node);
                }
            }
            batches.add(new Batch(batchIndex++, batchNodes));
            processed.addAll(currentLayer);

            // 2. 削减后继节点入度，计算下一层级
            List<String> nextLayer = new ArrayList<>();
            for (String nodeId : currentLayer) {
                for (String successor : adjacency.get(nodeId)) {
                    int newDeg = inDegree.get(successor) - 1;
                    inDegree.put(successor, newDeg);
                    if (newDeg == 0 && !processed.contains(successor)) {
                        nextLayer.add(successor);
                    }
                }
            }
            currentLayer = nextLayer;
        }

        // 3. 环检测：若输出节点数少于总数，说明存在环
        if (processed.size() != graph.getNodeIds().size()) {
            throw new BusinessException(ErrorCode.DAG_CYCLE_DETECTED,
                    "DAG 检测到环：批次划分处理 " + processed.size() + " 个节点，但实际有 "
                            + graph.getNodeIds().size() + " 个节点");
        }

        return batches;
    }
}
