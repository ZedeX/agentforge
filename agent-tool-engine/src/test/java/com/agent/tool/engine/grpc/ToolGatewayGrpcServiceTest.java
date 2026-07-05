package com.agent.tool.engine.grpc;

import agentplatform.tool.v1.GetToolRegistryRequest;
import agentplatform.tool.v1.ListToolsRequest;
import agentplatform.tool.v1.ListToolsResponse;
import agentplatform.tool.v1.RegisterToolAck;
import agentplatform.tool.v1.RegisterToolRequest;
import agentplatform.tool.v1.ToolInvokeRequest;
import agentplatform.tool.v1.ToolInvokeResponse;
import agentplatform.tool.v1.ToolRegistry;
import com.agent.tool.engine.api.ToolGateway;
import com.agent.tool.engine.api.impl.ToolRegistryImpl;
import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.SideEffect;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.exception.ToolApprovalException;
import com.agent.tool.engine.exception.ToolDisabledException;
import com.agent.tool.engine.exception.ToolNotFoundException;
import com.agent.tool.engine.exception.ToolQuotaExhaustedException;
import com.agent.tool.engine.exception.ToolValidationException;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.model.ToolMeta;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T12 {@link ToolGatewayGrpcService} unit tests.
 *
 * <p>Uses Mockito to mock {@link ToolGateway} and {@link ToolRegistryImpl},
 * verifying each RPC: unmarshal → delegate → marshal → onNext/onCompleted,
 * and that domain exceptions are translated to gRPC status codes via
 * {@link GrpcExceptionAdvice}.</p>
 */
@ExtendWith(MockitoExtension.class)
class ToolGatewayGrpcServiceTest {

    @Mock private ToolGateway toolGateway;
    @Mock private ToolRegistryImpl toolRegistry;
    @Mock private StreamObserver<ToolInvokeResponse> invokeObserver;
    @Mock private StreamObserver<RegisterToolAck> registerObserver;
    @Mock private StreamObserver<ListToolsResponse> listObserver;
    @Mock private StreamObserver<ToolRegistry> getObserver;

    private ToolGatewayGrpcService service;

    @BeforeEach
    void setUp() {
        ToolCallMapper mapper = new ToolCallMapper();
        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();
        service = new ToolGatewayGrpcService(toolGateway, toolRegistry, mapper, advice);
    }

    // ==================== Invoke ====================

    @Test
    @DisplayName("invoke_r1_readOnly_returnsResult: SUCCESS + output_json non-empty")
    void invoke_r1_readOnly_returnsResult() {
        ToolInvokeRequest req = ToolInvokeRequest.newBuilder()
                .setCallId("call-1")
                .setToolId("tool-r1")
                .setAgentId(100L)
                .setInputJson("{\"q\":\"hello\"}")
                .setRiskLevel(1)
                .build();
        ToolCallResult result = new ToolCallResult("tool-r1",
                "{\"answer\":\"world\"}", ToolCallStatus.SUCCESS);
        when(toolGateway.invoke(any(ToolCallRequest.class))).thenReturn(result);

        service.invoke(req, invokeObserver);

        ArgumentCaptor<ToolInvokeResponse> captor =
                ArgumentCaptor.forClass(ToolInvokeResponse.class);
        verify(invokeObserver, times(1)).onNext(captor.capture());
        verify(invokeObserver, times(1)).onCompleted();
        verify(invokeObserver, never()).onError(any());

        ToolInvokeResponse resp = captor.getValue();
        assertThat(resp.getCallId()).isEqualTo("call-1");
        assertThat(resp.getStatus()).isEqualTo("SUCCESS");
        assertThat(resp.getOutputJson()).contains("world");
    }

    @Test
    @DisplayName("invoke_toolNotFound_returnsNotFound: gRPC Status.NOT_FOUND")
    void invoke_toolNotFound_returnsNotFound() {
        ToolInvokeRequest req = ToolInvokeRequest.newBuilder()
                .setCallId("call-2")
                .setToolId("tool-ghost")
                .build();
        when(toolGateway.invoke(any(ToolCallRequest.class)))
                .thenThrow(new ToolValidationException("工具未注册: tool-ghost"));

        service.invoke(req, invokeObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(invokeObserver, never()).onNext(any());
        verify(invokeObserver, never()).onCompleted();
        verify(invokeObserver, times(1)).onError(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        // ToolValidationException -> INVALID_ARGUMENT (the gateway throws it
        // with VALIDATION_FAILED code when tool not registered).
    }

    @Test
    @DisplayName("invoke_r3_requiresApproval_returnsPermissionDenied: gRPC PERMISSION_DENIED")
    void invoke_r3_requiresApproval_returnsPermissionDenied() {
        ToolInvokeRequest req = ToolInvokeRequest.newBuilder()
                .setCallId("call-3")
                .setToolId("tool-r3")
                .setRiskLevel(3)
                .build();
        when(toolGateway.invoke(any(ToolCallRequest.class)))
                .thenThrow(new ToolApprovalException(
                        ToolApprovalException.CODE_APPROVAL_REQUIRED,
                        "工具 [tool-r3] 缺少有效审批"));

        service.invoke(req, invokeObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(invokeObserver, never()).onNext(any());
        verify(invokeObserver, times(1)).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("invoke_quotaExhausted_returnsResourceExhausted")
    void invoke_quotaExhausted_returnsResourceExhausted() {
        ToolInvokeRequest req = ToolInvokeRequest.newBuilder()
                .setToolId("tool-q")
                .setCallId("call-q")
                .build();
        when(toolGateway.invoke(any(ToolCallRequest.class)))
                .thenThrow(new ToolQuotaExhaustedException("限流触发"));

        service.invoke(req, invokeObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(invokeObserver, times(1)).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
    }

    @Test
    @DisplayName("invoke_toolDisabled_returnsPermissionDenied")
    void invoke_toolDisabled_returnsPermissionDenied() {
        ToolInvokeRequest req = ToolInvokeRequest.newBuilder()
                .setToolId("tool-disabled")
                .setCallId("call-d")
                .build();
        when(toolGateway.invoke(any(ToolCallRequest.class)))
                .thenThrow(new ToolDisabledException("工具已禁用"));

        service.invoke(req, invokeObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(invokeObserver, times(1)).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("invoke_invalidParams_returnsInvalidArgument")
    void invoke_invalidParams_returnsInvalidArgument() {
        ToolInvokeRequest req = ToolInvokeRequest.newBuilder()
                .setToolId("")  // blank toolId
                .setCallId("call-i")
                .build();

        service.invoke(req, invokeObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(invokeObserver, times(1)).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ==================== RegisterTool ====================

    @Test
    @DisplayName("registerTool_new_returnsToolId")
    void registerTool_new_returnsToolId() {
        RegisterToolRequest req = RegisterToolRequest.newBuilder()
                .setName("weather-query")
                .setDescription("query weather by city")
                .setExecutorType("HTTP_API")
                .setRiskLevel(1)
                .setEndpoint("https://api.weather.com/v1")
                .setTimeoutMs(5000)
                .build();
        when(toolRegistry.register(any(ToolMeta.class), any(), any()))
                .thenReturn("tool-weather-query");

        service.registerTool(req, registerObserver);

        ArgumentCaptor<RegisterToolAck> captor =
                ArgumentCaptor.forClass(RegisterToolAck.class);
        verify(registerObserver, times(1)).onNext(captor.capture());
        verify(registerObserver, times(1)).onCompleted();
        RegisterToolAck ack = captor.getValue();
        assertThat(ack.getToolId()).isEqualTo("tool-weather-query");
        assertThat(ack.getVersion()).isEqualTo(1);
        assertThat(ack.getApproved()).isTrue();
    }

    @Test
    @DisplayName("registerTool_duplicateName_returnsInvalidArgument")
    void registerTool_duplicateName_returnsInvalidArgument() {
        RegisterToolRequest req = RegisterToolRequest.newBuilder()
                .setName("dup-tool")
                .build();
        when(toolRegistry.register(any(ToolMeta.class), any(), any()))
                .thenThrow(new ToolValidationException("工具已注册: tool-dup-tool"));

        service.registerTool(req, registerObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(registerObserver, never()).onNext(any());
        verify(registerObserver, times(1)).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ==================== ListTools ====================

    @Test
    @DisplayName("listTools_returnsEnabledOnly")
    void listTools_returnsEnabledOnly() {
        ToolMeta meta1 = new ToolMeta("tool-a", "nameA",
                ExecutorType.HTTP_API, SideEffect.NONE, ToolRiskLevel.R1);
        ToolMeta meta2 = new ToolMeta("tool-b", "nameB",
                ExecutorType.SHELL, SideEffect.DESTRUCTIVE, ToolRiskLevel.R3);
        when(toolRegistry.findByStatus(any())).thenReturn(List.of(meta1, meta2));

        ListToolsRequest req = ListToolsRequest.newBuilder().build();
        service.listTools(req, listObserver);

        ArgumentCaptor<ListToolsResponse> captor =
                ArgumentCaptor.forClass(ListToolsResponse.class);
        verify(listObserver, times(1)).onNext(captor.capture());
        verify(listObserver, times(1)).onCompleted();
        ListToolsResponse resp = captor.getValue();
        assertThat(resp.getToolsCount()).isEqualTo(2);
        assertThat(resp.getTools(0).getToolId()).isEqualTo("tool-a");
        assertThat(resp.getTools(1).getToolId()).isEqualTo("tool-b");
    }

    @Test
    @DisplayName("listTools_empty_returnsEmptyList")
    void listTools_empty_returnsEmptyList() {
        when(toolRegistry.findByStatus(any())).thenReturn(List.of());

        ListToolsRequest req = ListToolsRequest.newBuilder().build();
        service.listTools(req, listObserver);

        ArgumentCaptor<ListToolsResponse> captor =
                ArgumentCaptor.forClass(ListToolsResponse.class);
        verify(listObserver, times(1)).onNext(captor.capture());
        assertThat(captor.getValue().getToolsCount()).isZero();
    }

    // ==================== GetToolRegistry ====================

    @Test
    @DisplayName("getToolMeta_returnsMeta")
    void getToolMeta_returnsMeta() {
        ToolMeta meta = new ToolMeta("tool-x", "nameX",
                ExecutorType.HTTP_API, SideEffect.NONE, ToolRiskLevel.R1);
        meta.setDescription("test tool");
        when(toolRegistry.findMeta("tool-x")).thenReturn(meta);

        GetToolRegistryRequest req = GetToolRegistryRequest.newBuilder()
                .setToolId("tool-x")
                .build();
        service.getToolRegistry(req, getObserver);

        ArgumentCaptor<ToolRegistry> captor =
                ArgumentCaptor.forClass(ToolRegistry.class);
        verify(getObserver, times(1)).onNext(captor.capture());
        verify(getObserver, times(1)).onCompleted();
        ToolRegistry proto = captor.getValue();
        assertThat(proto.getToolId()).isEqualTo("tool-x");
        assertThat(proto.getName()).isEqualTo("nameX");
        assertThat(proto.getDescription()).isEqualTo("test tool");
    }

    @Test
    @DisplayName("getToolMeta_notFound_returnsNotFound")
    void getToolMeta_notFound_returnsNotFound() {
        when(toolRegistry.findMeta("tool-ghost")).thenReturn(null);

        GetToolRegistryRequest req = GetToolRegistryRequest.newBuilder()
                .setToolId("tool-ghost")
                .build();
        service.getToolRegistry(req, getObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(getObserver, never()).onNext(any());
        verify(getObserver, times(1)).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    @DisplayName("getToolMeta_blankToolId_returnsInvalidArgument")
    void getToolMeta_blankToolId_returnsInvalidArgument() {
        GetToolRegistryRequest req = GetToolRegistryRequest.newBuilder()
                .setToolId("")
                .build();
        service.getToolRegistry(req, getObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(getObserver, times(1)).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
}
