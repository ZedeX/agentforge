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
 * DAG 节点实体（对齐 doc 03-task-engine §4.1 DagNode POJO 定义）。
 *
 * <p>11 业务字段：id / dagId / nodeId / nodeType / subtaskId / title / agentId /
 * abilityTags / inputs / outputs / status。审计字段 created_at / updated_at 继承自 BaseEntity。</p>
 *
 * <p>设计说明：JSON 字段（abilityTags / inputs / outputs）以 String 存储，
 * 由调用方负责序列化/反序列化，与 TaskInstance.taskSchema 风格一致。</p>
 *
 * <p>实现 {@link DagElement}：getDagId / getNodeId / getSubtaskId 三个方法
 * 由 Lombok @Data 自动生成的 getter 满足接口契约。</p>
 */
@Entity
@Table(name = "dag_node",
        uniqueConstraints = @UniqueConstraint(name = "uk_dag_node_id", columnNames = {"dag_id", "node_id"}),
        indexes = {
                @Index(name = "idx_dag_id", columnList = "dag_id"),
                @Index(name = "idx_subtask_id", columnList = "subtask_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class DagNode extends BaseEntity implements DagElement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "dag_id", nullable = false)
    private Long dagId;

    @Column(name = "node_id", length = 32, nullable = false)
    private String nodeId;

    @Column(name = "node_type", length = 16, nullable = false)
    private String nodeType;

    @Column(name = "subtask_id", length = 32)
    private String subtaskId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "ability_tags", columnDefinition = "json")
    private String abilityTags;

    @Column(name = "inputs", columnDefinition = "json")
    private String inputs;

    @Column(name = "outputs", columnDefinition = "json")
    private String outputs;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @PrePersist
    void prePersist() {
        touchTimestamps();
        if (this.status == null) {
            this.status = "pending";
        }
    }
}
