package com.agent.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 链路上下文（与 agent-proto 的 agentplatform.common.v1.TraceContext 字段一致）。
 * 用于 Java 业务代码内传递，避免直接依赖 protobuf 类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceContext {

    private Long tenantId;
    private String userId;
    private String sessionId;
    private String taskId;
    private String subtaskId;
    private String traceId;
    private String spanId;
}
