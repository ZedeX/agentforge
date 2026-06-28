package com.agent.gateway.adapter;

import agentplatform.task.v1.SubmitTaskRequest;
import agentplatform.task.v1.SubmitTaskResponse;
import com.agent.gateway.dto.TaskCreateRequest;
import com.agent.gateway.dto.TaskCreateResponse;
import com.agent.gateway.service.TaskRouterService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UT-F1-001: gRPC TaskOrchestrator 服务端实现（部署在 agent-gateway）。
 *
 * <p>职责：
 * <ol>
 *   <li>接收内部服务通过 gRPC SubmitTask 转发的任务请求</li>
 *   <li>委托 {@link ProtocolAdapter#adapt} 转换为 {@link TaskCreateRequest}（标记 internal=true）</li>
 *   <li>委托 {@link TaskRouterService#route} 路由到下游 orchestrator/session-service</li>
 *   <li>返回 {@link SubmitTaskResponse}</li>
 * </ol>
 * </p>
 *
 * <p>鉴权说明：gRPC 端口（默认 9091）独立于 HTTP（8080），
 * 不经过 {@link com.agent.gateway.filter.AuthFilter}。
 * 生产环境应通过 grpc.server.security 配置 mTLS 双向证书认证，
 * 此处实现仅做协议适配，不重复 JWT/API-Key 校验。</p>
 *
 * <p>注意：本服务名为 GrpcTaskService，但实现的是 proto 中定义的
 * {@code agentplatform.task.v1.TaskOrchestrator} 服务。
 * 与 agent-gateway 作为 gRPC <em>客户端</em> 调用 task-orchestrator 的
 * {@link com.agent.gateway.client.TaskOrchestratorClient} 角色互斥，
 * 后者后续应替换为 {@code @GrpcClient} 注入的 stub。</p>
 */
@GrpcService
public class GrpcTaskService extends agentplatform.task.v1.TaskOrchestratorGrpc.TaskOrchestratorImplBase {

    private static final Logger log = LoggerFactory.getLogger(GrpcTaskService.class);

    private final ProtocolAdapter protocolAdapter;
    private final TaskRouterService routerService;

    public GrpcTaskService(ProtocolAdapter protocolAdapter, TaskRouterService routerService) {
        this.protocolAdapter = protocolAdapter;
        this.routerService = routerService;
    }

    @Override
    public void submitTask(SubmitTaskRequest request,
                           StreamObserver<SubmitTaskResponse> responseObserver) {
        try {
            TaskCreateRequest taskRequest = protocolAdapter.adapt(request);
            String tenantId = normalizeTenantId(request.getTenantId());
            String userId = request.getUserId();

            log.info("grpc submitTask tenant={} user={} goal={} internal={}",
                    tenantId, userId, taskRequest.getGoal(), taskRequest.getInternal());

            TaskCreateResponse response = routerService.route(taskRequest, tenantId, userId);

            SubmitTaskResponse grpcResponse = SubmitTaskResponse.newBuilder()
                    .setTaskId(response.taskId())
                    .setStatus(response.status())
                    .setSubmittedAt(System.currentTimeMillis())
                    .build();

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            log.warn("grpc submitTask rejected reason={}", e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("grpc submitTask failed", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * proto 中 tenant_id 为 int64，需转换为字符串以适配 TaskRouterService.route 签名。
     * 默认值 0 视为缺失，回退为 "0"（与 REST API-Key 链路保持一致）。
     */
    private String normalizeTenantId(long tenantId) {
        if (tenantId <= 0) {
            return "0";
        }
        return Long.toString(tenantId);
    }
}
