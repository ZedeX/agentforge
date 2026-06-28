package com.agent.orchestrator.dag;

import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAG 图值对象，封装节点 + 边 + 邻接表 + 入度表的不可变快照。
 *
 * <p>由 {@link TopologicalSorter} 和 {@code DagValidator} 共享使用，
 * 避免每个算法重复构建邻接表/入度表。</p>
 *
 * <p>构造时一次性计算：</p>
 * <ul>
 *   <li>{@code nodeIds}：所有节点 ID 集合</li>
 *   <li>{@code adjacency}：parentNodeId -> List&lt;childNodeId&gt;（仅 DATA/LOGIC 边）</li>
 *   <li>{@code inDegree}：childNodeId -> 入度（仅 DATA/LOGIC 边贡献）</li>
 * </ul>
 *
 * <p>本类为不可变值对象：构造完成后所有内部状态只读，
 * 暴露的 List/Map 通过 {@link Collections#unmodifiableList(List)} 等包装防止外部修改。</p>
 */
public class DagGraph {

    private final List<DagNode> nodes;
    private final List<DagEdge> edges;
    private final Set<String> nodeIds;
    private final Map<String, List<String>> adjacency;
    private final Map<String, Integer> inDegree;

    public DagGraph(List<DagNode> nodes, List<DagEdge> edges) {
        this.nodes = nodes;
        this.edges = edges;

        // 1. 收集节点 ID
        Set<String> ids = new HashSet<>();
        for (DagNode node : nodes) {
            ids.add(node.getNodeId());
        }
        this.nodeIds = Collections.unmodifiableSet(ids);

        // 2. 初始化邻接表与入度表
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> deg = new HashMap<>();
        for (String id : ids) {
            adj.put(id, new ArrayList<>());
            deg.put(id, 0);
        }

        // 3. 仅 DATA/LOGIC 边构成依赖
        for (DagEdge edge : edges) {
            if (!"DATA".equals(edge.getEdgeType()) && !"LOGIC".equals(edge.getEdgeType())) {
                continue;
            }
            String from = edge.getParentNodeId();
            String to = edge.getChildNodeId();
            if (!ids.contains(from) || !ids.contains(to)) {
                continue;
            }
            adj.get(from).add(to);
            deg.put(to, deg.get(to) + 1);
        }

        // 4. 冻结邻接表内层 List（外层 Map 在 getAdjacency() 时整体包装）
        Map<String, List<String>> frozenAdj = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : adj.entrySet()) {
            frozenAdj.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        this.adjacency = Collections.unmodifiableMap(frozenAdj);
        this.inDegree = Collections.unmodifiableMap(deg);
    }

    public List<DagNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public List<DagEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public Set<String> getNodeIds() {
        return nodeIds;
    }

    public Map<String, List<String>> getAdjacency() {
        return adjacency;
    }

    public Map<String, Integer> getInDegree() {
        return inDegree;
    }

    /**
     * 返回入度为 0 的节点 ID 列表（拓扑排序的起始集合）。
     */
    public List<String> getZeroInDegreeNodeIds() {
        List<String> roots = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                roots.add(entry.getKey());
            }
        }
        return roots;
    }
}
