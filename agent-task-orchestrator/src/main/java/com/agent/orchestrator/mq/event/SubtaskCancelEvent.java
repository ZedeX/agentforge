package com.agent.orchestrator.mq.event;

import lombok.Builder;
import lombok.Data;

/**
 * 子任务取消事件（对齐 doc 03-task-engine §7.1 task.subtask.cancel Topic）。
 * Topic: task.subtask.cancel / 消费者: agent-runtime
 */
@Data
@Builder
public class SubtaskCancelEvent {
    private String eventId;
    private String eventType;          // "task.subtask.cancel"
    private String eventTime;
    private String traceId;
    private Long tenantId;
    private String taskId;
    private String nodeId;
    private String subtaskId;
    private String reason;
}
