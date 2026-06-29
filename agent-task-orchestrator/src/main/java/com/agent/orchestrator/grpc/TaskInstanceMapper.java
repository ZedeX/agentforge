package com.agent.orchestrator.grpc;

import agentplatform.task.v1.TaskInstance;
import com.agent.orchestrator.model.BaseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * proto TaskInstance ↔ JPA TaskInstance 映射器。
 *
 * <p>两个 TaskInstance 同名：proto 类位于 {@code agentplatform.task.v1.TaskInstance}，
 * JPA 实体位于 {@code com.agent.orchestrator.model.TaskInstance}。本类用 FQN 消歧义：
 * 方法返回值/参数中的 proto 类型使用 FQN {@code agentplatform.task.v1.TaskInstance}，
 * JPA 实体类型使用 FQN {@code com.agent.orchestrator.model.TaskInstance}。</p>
 *
 * <p>映射规则（对齐 task.proto 24 字段 + task_instance 表 23 业务字段 + 2 审计字段）：</p>
 * <ul>
 *   <li>JPA → proto：直接字段拷贝，Instant → epoch millis（null 时填 0）</li>
 *   <li>proto 请求 → JPA：仅从 SubmitTaskRequest 提取字段，其余由 JPA @PrePersist 填默认值</li>
 * </ul>
 */
@Component
public class TaskInstanceMapper {

    /**
     * JPA 实体 → proto 消息。
     *
     * @param entity JPA 任务实例（不可为 null）
     * @return proto TaskInstance 消息
     */
    public TaskInstance toProto(com.agent.orchestrator.model.TaskInstance entity) {
        TaskInstance.Builder b = TaskInstance.newBuilder()
                .setTaskId(safe(entity.getTaskId()))
                .setTenantId(safeLong(entity.getTenantId()))
                .setSessionId(safe(entity.getSessionId()))
                .setUserId(safe(entity.getUserId()))
                .setTitle(safe(entity.getTitle()))
                .setGoal(safe(entity.getGoal()))
                .setComplexity(safeInt(entity.getComplexity()))
                .setStatus(safe(entity.getStatus()))
                .setTaskSchema(safe(entity.getTaskSchema()))
                .setDagId(safeLong(entity.getDagId()))
                .setAgentId(safeLong(entity.getAgentId()))
                .setPriority(safeInt(entity.getPriority()))
                .setParentTaskId(safe(entity.getParentTaskId()))
                .setReplanCount(entity.getReplanCount())
                .setCostLimitCent(safeLong(entity.getCostLimitCent()))
                .setCostUsedCent(safeLong(entity.getCostUsedCent()))
                .setTokenUsed(safeInt(entity.getTokenUsed()))
                .setErrorCode(safe(entity.getErrorCode()))
                .setErrorMsg(safe(entity.getErrorMsg()))
                .setResultSummary(safe(entity.getResultSummary()));

        if (entity.getStartedAt() != null) {
            b.setStartedAt(entity.getStartedAt().toEpochMilli());
        }
        if (entity.getFinishedAt() != null) {
            b.setFinishedAt(entity.getFinishedAt().toEpochMilli());
        }
        BaseEntity audit = entity;
        if (audit.getCreatedAt() != null) {
            b.setCreatedAt(audit.getCreatedAt().toEpochMilli());
        }
        if (audit.getUpdatedAt() != null) {
            b.setUpdatedAt(audit.getUpdatedAt().toEpochMilli());
        }
        return b.build();
    }

    /**
     * proto 提交请求 → JPA 实体（新建，未持久化）。
     *
     * <p>从 SubmitTaskRequest 提取 taskId/tenantId/sessionId/userId/title/goal/priority/
     * costLimitCent，complexity 由调用方传入。其余字段（status/taskSchema/dagId 等）由
     * 调用方或 JPA @PrePersist 填默认值。</p>
     *
     * @param request    proto 提交请求
     * @param complexity 任务复杂度（1=L1 2=L2 3=L3）
     * @return 未持久化的 JPA 实体
     */
    public com.agent.orchestrator.model.TaskInstance fromSubmitRequest(
            agentplatform.task.v1.SubmitTaskRequest request, int complexity) {
        return com.agent.orchestrator.model.TaskInstance.builder()
                .taskId(request.getTaskId())
                .tenantId(request.getTenantId())
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .title(request.getTitle().isEmpty() ? request.getGoal() : request.getTitle())
                .goal(request.getGoal())
                .complexity(complexity)
                .priority(request.getPriority())
                .costLimitCent(request.getCostLimitCent())
                .costUsedCent(0L)
                .tokenUsed(0)
                .replanCount(0)
                .taskSchema("{}")
                .build();
    }

    /**
     * epoch millis → Instant 辅助。
     *
     * @param millis epoch 毫秒（0 或负数返回 null）
     * @return Instant 或 null
     */
    private Instant millisToInstant(long millis) {
        if (millis <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(millis);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private long safeLong(Long v) {
        return v == null ? 0L : v;
    }

    private long safeLong(long v) {
        return v;
    }

    private int safeInt(Integer v) {
        return v == null ? 0 : v;
    }

    private int safeInt(int v) {
        return v;
    }
}
