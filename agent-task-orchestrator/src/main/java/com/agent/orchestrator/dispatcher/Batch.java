package com.agent.orchestrator.dispatcher;

import com.agent.orchestrator.model.DagNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 批次 POJO（对齐 doc 03-task-engine §4.2 parallel_batches 概念）。
 *
 * <p>同一批次内的 {@link DagNode} 无相互依赖，可并行投递到 {@code task.subtask.execute} Topic。
 * 当前批次全部 success 后才能推进下一批次。</p>
 *
 * <p>批次索引 {@code batchIndex} 从 0 开始，与 {@code parallel_batches JSON} 中的数组下标对齐。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Batch {

    /**
     * 批次索引，从 0 开始。
     */
    private int batchIndex;

    /**
     * 批次内节点列表（同批次节点间无依赖，可并行执行）。
     */
    private List<DagNode> nodes;

    /**
     * 返回批次内节点 ID 列表（按 {@link #nodes} 顺序）。
     *
     * @return 节点 ID 列表；若 nodes 为空返回空列表
     */
    public List<String> getNodeIds() {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        return nodes.stream()
                .map(DagNode::getNodeId)
                .collect(Collectors.toList());
    }
}
