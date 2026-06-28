package com.agent.orchestrator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * DAG 边实体（对齐 doc 03-task-engine §4.1 DagEdge POJO 定义 + T3.1 任务要求）。
 *
 * <p>6 业务字段：id / dagId / parentNodeId / childNodeId / edgeType / paramMapping。
 * 审计字段 created_at / updated_at 继承自 BaseEntity。</p>
 *
 * <p>命名说明：doc 中字段为 from / to / depType，本模块按 T3.1 任务要求
 * 改为 parentNodeId / childNodeId / edgeType，与 DDL 列命名风格一致。</p>
 *
 * <p>edgeType 取值：DATA（数据依赖）/ LOGIC（逻辑依赖）/ NONE（无依赖，仅并行批次标记）。</p>
 */
@Entity
@Table(name = "dag_edge",
        uniqueConstraints = @UniqueConstraint(name = "uk_dag_edge", columnNames = {"dag_id", "parent_node_id", "child_node_id"}),
        indexes = {
                @Index(name = "idx_dag_id", columnList = "dag_id"),
                @Index(name = "idx_parent_node", columnList = "parent_node_id"),
                @Index(name = "idx_child_node", columnList = "child_node_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class DagEdge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "dag_id", nullable = false)
    private Long dagId;

    @Column(name = "parent_node_id", length = 32, nullable = false)
    private String parentNodeId;

    @Column(name = "child_node_id", length = 32, nullable = false)
    private String childNodeId;

    @Column(name = "edge_type", length = 16, nullable = false)
    private String edgeType;

    @Column(name = "param_mapping", columnDefinition = "json")
    private String paramMapping;

    @PrePersist
    void prePersist() {
        touchTimestamps();
    }
}
