package com.agent.orchestrator.mq.event;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 子任务完成事件（对齐 doc 03-task-engine §7.4）。
 * Topic: task.subtask.done / 消费者: task-orchestrator
 */
@Data
@Builder
public class SubtaskDoneEvent {
    private String eventId;
    private String eventType;          // "task.subtask.done"
    private String eventTime;
    private String traceId;
    private Long tenantId;
    private String taskId;
    private String subtaskId;
    private String nodeId;
    private String status;             // success | failed | timeout | require_review
    private Map<String, Object> outputs;
    private Integer tokenUsed;
    private Long costCent;
    private Integer durationMs;
    private String errorCode;
    private String errorMsg;
}
