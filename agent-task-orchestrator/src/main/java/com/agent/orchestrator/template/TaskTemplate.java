package com.agent.orchestrator.template;

import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务模板 POJO（对齐 doc 03-task-engine §3.2 模板匹配算法 + §8.3 task_template 概念）。
 *
 * <p>承载模板匹配所需字段：场景标签、参数 schema、模板 DAG（节点 + 边）、
 * 成功率与使用次数（用于排序）、状态（2=启用，其他=禁用）。</p>
 *
 * <p>设计说明：本类为纯 POJO，不依赖数据库（{@code status} 字段以 int 表示，
 * 2 对应 doc 中 status=2 启用）。模板 DAG 直接复用 {@link DagNode}/{@link DagEdge}，
 * 避免新增冗余类型。参数 schema 以 JSON 字符串简化存储，由调用方负责序列化。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskTemplate {

    /**
     * 模板 ID（对应 task_template.template_id）。
     */
    private String templateId;

    /**
     * 模板标题。
     */
    private String title;

    /**
     * 场景标签列表（用于按 scene_tags 精确匹配）。
     */
    private List<String> sceneTags;

    /**
     * 参数 schema（JSON 字符串，简化处理）。
     */
    private String paramSchema;

    /**
     * 模板 DAG 节点（复用 com.agent.orchestrator.model.DagNode）。
     */
    private List<DagNode> dagNodes;

    /**
     * 模板 DAG 边（复用 com.agent.orchestrator.model.DagEdge）。
     */
    private List<DagEdge> dagEdges;

    /**
     * 成功率，取值 [0.0, 1.0]，阈值 0.85（对应 doc §3.2 success_rate&gt;=0.85）。
     */
    private double successRate;

    /**
     * 使用次数，越大优先级越高（排序字段 1，对应 doc §3.2 ORDER BY usage_count DESC）。
     */
    private int usageCount;

    /**
     * 状态：2=启用，其他=禁用（对应 doc §3.2 status=2）。
     */
    private int status;
}
