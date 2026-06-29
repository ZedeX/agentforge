package com.agent.gateway.adapter;

import agentplatform.task.v1.SubmitTaskRequest;
import agentplatform.task.v1.SubmitTaskResponse;
import com.agent.gateway.dto.TaskCreateRequest;
import com.agent.gateway.dto.TaskCreateResponse;
import com.agent.gateway.service.TaskRouterService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UT-F1-001: Should_AdaptGrpcProtocol_When_InternalServiceCall
 *
 * <p>验证 gRPC SubmitTask 内部调用适配为 Task 的行为：
 * <ol>
 *   <li>gRPC SubmitTaskRequest 适配为 TaskCreateRequest（含 goal + internal=true）</li>
 *   <li>gRPC 来源 internal=true，REST 来源 internal=false</li>
 *   <li>goal 字段透传不丢失</li>
 *   <li>GrpcTaskService 委托 ProtocolAdapter + TaskRouterService 完成 gRPC 调用</li>
 * </ol>
 *
 * <p>mTLS 握手复杂度高，此处只验证适配逻辑，不测试传输层安全；
 * AuthFilter 层的内部调用判定另见 {@code AuthFilterTest}。</p>
 */
class ProtocolAdapterTest {

    private ProtocolAdapter protocolAdapter;
    private TaskRouterService routerService;
    private GrpcTaskService grpcTaskService;

    @BeforeEach
    void setUp() {
        protocolAdapter = new ProtocolAdapter();
        routerService = mock(TaskRouterService.class);
        grpcTaskService = new GrpcTaskService(protocolAdapter, routerService);
    }

    @Test
    @DisplayName("gRPC SubmitTaskRequest 适配为 TaskCreateRequest 时应透传全部字段并标记 internal=true")
    void should_AdaptGrpcToTask_When_SubmitTaskReceived() {
        SubmitTaskRequest grpcReq = SubmitTaskRequest.newBuilder()
                .setGoal("生成本周销售周报")
                .setTitle("周报任务")
                .setTenantId(1001L)
                .setUserId("u_internal_001")
                .setSessionId("ss_internal_001")
                .setPriority(5)
                .setCostLimitCent(5000L)
                .build();

        TaskCreateRequest result = protocolAdapter.adapt(grpcReq);

        assertThat(result).isNotNull();
        assertThat(result.getGoal()).isEqualTo("生成本周销售周报");
        assertThat(result.getTitle()).isEqualTo("周报任务");
        assertThat(result.getSessionId()).isEqualTo("ss_internal_001");
        assertThat(result.getPriority()).isEqualTo(5);
        assertThat(result.getCostLimitCent()).isEqualTo(5000L);
        assertThat(result.getInternal()).as("gRPC 来源应标记 internal=true").isTrue();
    }

    @Test
    @DisplayName("gRPC 来源应标记 internal=true，REST 来源应保持 internal=false 默认值")
    void should_SetInternalFlagTrue_When_FromGrpc() {
        SubmitTaskRequest grpcReq = SubmitTaskRequest.newBuilder()
                .setGoal("内部 gRPC 任务")
                .build();

        TaskCreateRequest grpcResult = protocolAdapter.adapt(grpcReq);
        TaskCreateRequest restResult = new TaskCreateRequest();
        restResult.setGoal("REST 任务");

        assertThat(grpcResult.getInternal()).as("gRPC 来源应标记 internal=true").isTrue();
        assertThat(restResult.getInternal()).as("REST 来源应保持 internal=false 默认值").isFalse();
    }

    @Test
    @DisplayName("goal 字段在适配过程中应原样透传不丢失或截断")
    void should_PreserveGoal_When_Adapting() {
        String longGoal = "这是一个较长的目标字符串，用于验证适配过程中 goal 字段不会丢失或被截断";
        SubmitTaskRequest grpcReq = SubmitTaskRequest.newBuilder()
                .setGoal(longGoal)
                .build();

        TaskCreateRequest result = protocolAdapter.adapt(grpcReq);

        assertThat(result.getGoal()).as("goal 字段应原样透传").isEqualTo(longGoal);
    }

    @Test
    @DisplayName("GrpcTaskService 应委托 routerService 路由并返回 SubmitTaskResponse 给 observer")
    void should_DelegateToRouterAndReturnResponse_When_GrpcSubmitTask() {
        SubmitTaskRequest grpcReq = SubmitTaskRequest.newBuilder()
                .setGoal("测试 gRPC 转发")
                .setTitle("gRPC 测试")
                .setTenantId(1001L)
                .setUserId("u_internal_002")
                .build();

        when(routerService.route(any(TaskCreateRequest.class), eq("1001"), eq("u_internal_002")))
                .thenReturn(new TaskCreateResponse("tk_grpc_001", "PENDING"));

        @SuppressWarnings("unchecked")
        StreamObserver<SubmitTaskResponse> observer = mock(StreamObserver.class);

        grpcTaskService.submitTask(grpcReq, observer);

        verify(routerService).route(any(TaskCreateRequest.class), eq("1001"), eq("u_internal_002"));
        verify(observer).onNext(argThat(resp ->
                "tk_grpc_001".equals(resp.getTaskId()) && "PENDING".equals(resp.getStatus())));
        verify(observer).onCompleted();
        verify(observer, never()).onError(any());
    }
}
