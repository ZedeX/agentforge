package com.agent.orchestrator.mq.event;

import lombok.Builder;
import lombok.Data;

/**
 * 任务状态变更广播事件（对齐 doc 03-task-engine §6.3 审计格式）。
 * Topic: task.state.change / 消费者: session / observability
 */
@Data
@Builder
public class StateChangeEvent {
    private String taskId;
    private String fromStatus;
    private String toStatus;
    private String trigger;            // auto | manual | system
    private String operator;
    private String reason;
    private String traceId;
    private Long tenantId;
    private String createdAt;          // ISO-8601
}
