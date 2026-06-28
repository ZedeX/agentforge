package com.agent.gateway.adapter;

import agentplatform.task.v1.SubmitTaskRequest;
import com.agent.gateway.dto.TaskCreateRequest;
import org.springframework.stereotype.Component;

/**
 * UT-F1-001: 协议适配器。
 *
 * <p>将内部 gRPC {@link SubmitTaskRequest} 适配为网关内部统一处理的
 * {@link TaskCreateRequest}。适配后的 Task 含 {@code internal=true} 标记，
 * 后续链路据此跳过 JWT/API-Key 校验（改走 mTLS）。</p>
 *
 * <p>字段映射（proto -> dto）：
 * <ul>
 *   <li>{@code goal}        -> {@code goal}（透传）</li>
 *   <li>{@code title}       -> {@code title}</li>
 *   <li>{@code session_id}  -> {@code sessionId}</li>
 *   <li>{@code priority}    -> {@code priority}</li>
 *   <li>{@code cost_limit_cent} -> {@code costLimitCent}</li>
 *   <li>{@code type}（proto 无此字段）-> 默认 {@code "single_step"}</li>
 *   <li>{@code internal}（dto 新增） -> 固定 {@code true}</li>
 * </ul>
 * </p>
 *
 * <p>注意：proto {@code tenant_id}/{@code user_id} 不进入 TaskCreateRequest，
 * 而是直接作为 {@link com.agent.gateway.service.TaskRouterService#route} 的
 * 第二/第三参数透传，避免在 dto 内重复存储租户上下文。</p>
 */
@Component
public class ProtocolAdapter {

    /**
     * gRPC 内部 SubmitTask 默认归为 single_step 类型。
     *
     * <p>proto 未携带 type 字段，且 gRPC SubmitTask 通常对应单步任务；
     * 如未来需区分 chat/complex，应扩展 proto 或在请求 metadata 中携带。</p>
     */
    public static final String DEFAULT_GRPC_TASK_TYPE = "single_step";

    /**
     * 将 gRPC SubmitTaskRequest 适配为 TaskCreateRequest。
     *
     * @param grpcRequest gRPC 提交任务请求（不可为 null）
     * @return 含 goal + internal=true 的 TaskCreateRequest
     */
    public TaskCreateRequest adapt(SubmitTaskRequest grpcRequest) {
        if (grpcRequest == null) {
            throw new IllegalArgumentException("grpcRequest must not be null");
        }

        TaskCreateRequest task = new TaskCreateRequest();
        task.setType(DEFAULT_GRPC_TASK_TYPE);
        task.setGoal(grpcRequest.getGoal());
        task.setTitle(grpcRequest.getTitle());
        task.setSessionId(grpcRequest.getSessionId());
        task.setPriority(grpcRequest.getPriority());
        task.setCostLimitCent(grpcRequest.getCostLimitCent());
        task.setInternal(true);
        return task;
    }
}
