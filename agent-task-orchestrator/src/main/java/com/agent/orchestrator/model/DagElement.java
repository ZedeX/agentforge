package com.agent.orchestrator.model;

/**
 * DAG 元素公共接口，由 {@link DagNode} 和 {@link DagEdge} 实现。
 *
 * <p>抽象"属于某个 DAG 的元素"的公共契约，便于后续校验器（DagValidator）
 * 与拓扑排序器（TopologicalSorter）以统一方式访问元素归属信息。</p>
 *
 * <p>语义说明：</p>
 * <ul>
 *   <li>{@link #getDagId()}：元素所属 DAG 的业务 ID（节点和边均具备）。</li>
 *   <li>{@link #getNodeId()}：对于 {@link DagNode} 返回节点自身 ID；
 *       对于 {@link DagEdge} 返回 {@code parentNodeId}（边的源节点 ID，
 *       用于在边集合中按节点维度索引）。</li>
 *   <li>{@link #getSubtaskId()}：对于 {@link DagNode} 返回关联子任务业务 ID
 *       （start/end 节点可为 null）；对于 {@link DagEdge} 始终返回 null
 *       （边本身不承载子任务语义）。</li>
 * </ul>
 */
public interface DagElement {

    Long getDagId();

    String getNodeId();

    String getSubtaskId();
}
