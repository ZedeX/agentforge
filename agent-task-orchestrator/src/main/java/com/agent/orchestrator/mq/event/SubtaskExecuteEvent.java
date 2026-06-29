package com.agent.orchestrator.mq.event;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 子任务分发事件（对齐 doc 03-task-engine §7.2 消息格式）。
 * Topic: task.subtask.execute / Key: {taskId}:{nodeId} / Tag: {tenantId}
 */
@Data
@Builder
public class SubtaskExecuteEvent {
    private String eventId;
    private String eventType;          // 固定 "task.subtask.execute"
    private String eventTime;          // ISO-8601
    private String traceId;
    private Long tenantId;
    // payload 字段
    private String taskId;
    private Long dagId;
    private Integer dagVersion;
    private String nodeId;
    private String subtaskId;
    private Long agentId;
    private String title;
    private List<String> abilityTags;
    private Map<String, Object> inputs;
    private SubtaskConfig config;       // maxRetries / timeoutMs / modelTier / requireHumanReview
    private String deadline;
    private Long costBudgetCent;

    @Data
    @Builder
    public static class SubtaskConfig {
        private Integer maxRetries;
        private Integer timeoutMs;
        private String modelTier;
        private Boolean requireHumanReview;
    }
}
