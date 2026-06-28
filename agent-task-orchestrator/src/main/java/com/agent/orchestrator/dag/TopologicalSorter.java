package com.agent.orchestrator.dag;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * DAG 拓扑排序器（Kahn 算法），对齐 doc 03-task-engine §4.2。
 *
 * <p>仅 DATA / LOGIC 边构成依赖（NONE 不构成）。
 * 按入度递减顺序输出节点 ID 序列；若存在环（输出节点数少于总数），
 * 抛 {@link BusinessException}({@link ErrorCode#DAG_CYCLE_DETECTED})。</p>
 *
 * <p>内部通过 {@link DagGraph} 值对象复用邻接表与入度表，
 * 避免调用方与 {@code DagValidator} 重复构建图结构。</p>
 */
public class TopologicalSorter {

    /**
     * 对节点与边构成的 DAG 执行拓扑排序。
     *
     * @param nodes DAG 节点列表（不可为 null，可为空）
     * @param edges DAG 边列表（不可为 null，可为空）
     * @return 节点 ID 的拓扑顺序列表
     * @throws BusinessException 当检测到环时，错误码为 {@link ErrorCode#DAG_CYCLE_DETECTED}
     */
    public List<String> sort(List<DagNode> nodes, List<DagEdge> edges) {
        DagGraph graph = new DagGraph(nodes, edges);
        return sort(graph);
    }

    /**
     * 对 {@link DagGraph} 执行拓扑排序。
     *
     * @param graph 已构建好的 DAG 图值对象
     * @return 节点 ID 的拓扑顺序列表
     * @throws BusinessException 当检测到环时，错误码为 {@link ErrorCode#DAG_CYCLE_DETECTED}
     */
    public List<String> sort(DagGraph graph) {
        if (graph.getNodeIds().isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<String>> adjacency = graph.getAdjacency();
        // 拷贝入度表，避免修改原图状态（Kahn 算法会递减入度）
        java.util.HashMap<String, Integer> inDegree = new java.util.HashMap<>(graph.getInDegree());

        // 1. 入度为 0 的节点入队作为起始
        Queue<String> queue = new LinkedList<>(graph.getZeroInDegreeNodeIds());

        // 2. Kahn 算法主体：逐个出队并削减后继节点入度
        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(current);
            for (String successor : adjacency.get(current)) {
                int newDeg = inDegree.get(successor) - 1;
                inDegree.put(successor, newDeg);
                if (newDeg == 0) {
                    queue.offer(successor);
                }
            }
        }

        // 3. 环检测：若输出节点数少于总数，说明存在环
        if (sorted.size() != graph.getNodeIds().size()) {
            throw new BusinessException(ErrorCode.DAG_CYCLE_DETECTED,
                    "DAG 检测到环：拓扑排序输出 " + sorted.size() + " 个节点，但实际有 "
                            + graph.getNodeIds().size() + " 个节点");
        }

        return sorted;
    }
}
