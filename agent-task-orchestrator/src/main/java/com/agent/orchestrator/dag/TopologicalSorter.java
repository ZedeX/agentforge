package com.agent.orchestrator.dag;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * DAG 拓扑排序器（Kahn 算法），对齐 doc 03-task-engine §4.2。
 *
 * <p>仅 DATA / LOGIC 边构成依赖（NONE 不构成）。
 * 按入度递减顺序输出节点 ID 序列；若存在环（输出节点数少于总数），
 * 抛 {@link BusinessException}({@link ErrorCode#DAG_CYCLE_DETECTED})。</p>
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
        if (nodes.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 收集所有节点 ID
        Set<String> nodeIds = new HashSet<>();
        for (DagNode node : nodes) {
            nodeIds.add(node.getNodeId());
        }

        // 2. 构建邻接表与入度表（仅 DATA/LOGIC 边构成依赖）
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String id : nodeIds) {
            adjacency.put(id, new ArrayList<>());
            inDegree.put(id, 0);
        }
        for (DagEdge edge : edges) {
            if (!"DATA".equals(edge.getEdgeType()) && !"LOGIC".equals(edge.getEdgeType())) {
                continue;
            }
            String from = edge.getParentNodeId();
            String to = edge.getChildNodeId();
            if (!nodeIds.contains(from) || !nodeIds.contains(to)) {
                continue;
            }
            adjacency.get(from).add(to);
            inDegree.put(to, inDegree.get(to) + 1);
        }

        // 3. 入度为 0 的节点入队作为起始
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        // 4. Kahn 算法主体：逐个出队并削减后继节点入度
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

        // 5. 环检测：若输出节点数少于总数，说明存在环
        if (sorted.size() != nodeIds.size()) {
            throw new BusinessException(ErrorCode.DAG_CYCLE_DETECTED,
                    "DAG 检测到环：拓扑排序输出 " + sorted.size() + " 个节点，但实际有 " + nodeIds.size() + " 个节点");
        }

        return sorted;
    }
}
