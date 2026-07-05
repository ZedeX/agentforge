package com.agent.runtime.api.impl;

import agentplatform.tool.v1.ToolGatewayGrpc;
import com.agent.runtime.api.dto.ToolInvokeRequest;
import com.agent.runtime.api.dto.ToolInvokeResult;
import com.agent.runtime.config.RuntimeProperties;
import com.agent.runtime.exception.ToolApprovalRequiredException;
import com.agent.runtime.exception.ToolExecutionTimeoutException;
import com.agent.runtime.exception.ToolNotFoundException;
import com.agent.runtime.exception.ToolQuotaExhaustedException;
import com.agent.runtime.fixture.FakeToolGatewayService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * T5 ToolEngineClientImpl 单元测试 (gRPC in-process server).
 *
 * <p>验证 {@link ToolEngineClientImpl} 通过真实 gRPC 链路调用
 * {@link FakeToolGatewayService} 的 8 个场景:
 * <ol>
 *   <li>{@code callTool_returnsResult} — toolId + params → 返回 ToolInvokeResult (status=success)</li>
 *   <li>{@code callTool_passesAgentContext} — 验证请求体包含 callId / taskId / agentId</li>
 *   <li>{@code callTool_throwsOnNotFound} — NOT_FOUND → {@link ToolNotFoundException}</li>
 *   <li>{@code callTool_throwsOnApprovalRequired} — PERMISSION_DENIED → {@link ToolApprovalRequiredException}</li>
 *   <li>{@code callTool_throwsOnQuotaExhausted} — RESOURCE_EXHAUSTED → {@link ToolQuotaExhaustedException}</li>
 *   <li>{@code callTool_throwsOnTimeout} — DEADLINE_EXCEEDED → {@link ToolExecutionTimeoutException}</li>
 *   <li>{@code callTool_returnsCleanedStdout} — outputJson 已清洗 (无 PII)</li>
 *   <li>{@code callTool_recordsCallIdAndCacheFlag} — 返回 callId / fromCache</li>
 * </ol>
 *
 * <p>基础设施: {@link InProcessServerBuilder} + {@link InProcessChannelBuilder}, directExecutor,
 * 无真实端口、无 Spring 上下文、无 Docker, 启动开销极小。
 */
class ToolEngineClientImplTest {

    private static Server grpcServer;
    private static ManagedChannel channel;
    private static ToolEngineClientImpl client;
    private static FakeToolGatewayService fakeService;

    @BeforeAll
    static void setUp() throws Exception {
        fakeService = new FakeToolGatewayService();
        String serverName = InProcessServerBuilder.generateName();
        grpcServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(fakeService)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        ToolGatewayGrpc.ToolGatewayBlockingStub stub =
                ToolGatewayGrpc.newBlockingStub(channel);
        client = new ToolEngineClientImpl(stub, new RuntimeProperties());
    }

    @AfterAll
    static void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
    }

    @Test
    @DisplayName("1. callTool_returnsResult: toolId + params 应返回 status=success 的 ToolInvokeResult")
    void callTool_returnsResult() {
        ToolInvokeRequest req = ToolInvokeRequest.builder()
                .callId("normal_001")
                .taskId("task_001")
                .agentId(1001L)
                .toolId("search")
                .inputJson("{\"q\":\"hello\"}")
                .build();

        ToolInvokeResult result = client.invoke(req);

        assertThat(result)
                .as("非 null 结果")
                .isNotNull();
        assertThat(result.getStatus())
                .as("status 应为 success")
                .isEqualTo("success");
        assertThat(result.getOutputJson())
                .as("outputJson 应镜像 inputJson")
                .isEqualTo("{\"q\":\"hello\"}");
    }

    @Test
    @DisplayName("2. callTool_passesAgentContext: 请求体应包含 callId / taskId / agentId")
    void callTool_passesAgentContext() {
        ToolInvokeRequest req = ToolInvokeRequest.builder()
                .callId("normal_002")
                .taskId("task_002")
                .stepNo(3)
                .agentId(2002L)
                .toolId("calculator")
                .inputJson("{}")
                .build();

        client.invoke(req);

        agentplatform.tool.v1.ToolInvokeRequest captured = fakeService.getLastRequest();
        assertThat(captured)
                .as("fake service 应捕获到请求")
                .isNotNull();
        assertThat(captured.getCallId()).isEqualTo("normal_002");
        assertThat(captured.getTaskId()).isEqualTo("task_002");
        assertThat(captured.getStepNo()).isEqualTo(3);
        assertThat(captured.getAgentId()).isEqualTo(2002L);
        assertThat(captured.getToolId()).isEqualTo("calculator");
    }

    @Test
    @DisplayName("3. callTool_throwsOnNotFound: gRPC NOT_FOUND 应抛 ToolNotFoundException")
    void callTool_throwsOnNotFound() {
        ToolInvokeRequest req = ToolInvokeRequest.builder()
                .callId("notfound_003")
                .toolId("ghost")
                .build();

        ToolNotFoundException ex = catchThrowableOfType(
                () -> client.invoke(req),
                ToolNotFoundException.class);

        assertThat(ex)
                .as("NOT_FOUND 应翻译为 ToolNotFoundException")
                .isNotNull();
        assertThat(ex.getMessage())
                .as("异常消息应包含 toolId")
                .contains("ghost");
    }

    @Test
    @DisplayName("4. callTool_throwsOnApprovalRequired: PERMISSION_DENIED 应抛 ToolApprovalRequiredException 并携带 approvalCallId")
    void callTool_throwsOnApprovalRequired() {
        ToolInvokeRequest req = ToolInvokeRequest.builder()
                .callId("approval_004")
                .toolId("risk_tool")
                .build();

        ToolApprovalRequiredException ex = catchThrowableOfType(
                () -> client.invoke(req),
                ToolApprovalRequiredException.class);

        assertThat(ex)
                .as("PERMISSION_DENIED 应翻译为 ToolApprovalRequiredException")
                .isNotNull();
        assertThat(ex.getApprovalCallId())
                .as("应携带 approval_call_id (从 description 解析)")
                .isEqualTo("apc_test_001");
    }

    @Test
    @DisplayName("5. callTool_throwsOnQuotaExhausted: RESOURCE_EXHAUSTED 应抛 ToolQuotaExhaustedException")
    void callTool_throwsOnQuotaExhausted() {
        ToolInvokeRequest req = ToolInvokeRequest.builder()
                .callId("quota_005")
                .toolId("rate_limited_tool")
                .build();

        ToolQuotaExhaustedException ex = catchThrowableOfType(
                () -> client.invoke(req),
                ToolQuotaExhaustedException.class);

        assertThat(ex)
                .as("RESOURCE_EXHAUSTED 应翻译为 ToolQuotaExhaustedException")
                .isNotNull();
    }

    @Test
    @DisplayName("6. callTool_throwsOnTimeout: DEADLINE_EXCEEDED 应抛 ToolExecutionTimeoutException")
    void callTool_throwsOnTimeout() {
        ToolInvokeRequest req = ToolInvokeRequest.builder()
                .callId("timeout_006")
                .toolId("slow_tool")
                .build();

        ToolExecutionTimeoutException ex = catchThrowableOfType(
                () -> client.invoke(req),
                ToolExecutionTimeoutException.class);

        assertThat(ex)
                .as("DEADLINE_EXCEEDED 应翻译为 ToolExecutionTimeoutException")
                .isNotNull();
    }

    @Test
    @DisplayName("7. callTool_returnsCleanedStdout: outputJson 应已被 tool-engine 清洗 (无 PII)")
    void callTool_returnsCleanedStdout() {
        ToolInvokeRequest req = ToolInvokeRequest.builder()
                .callId("cleaned_007")
                .toolId("email_parser")
                .inputJson("{\"raw\":\"contact me at test@example.com or 13800138000\"}")
                .build();

        ToolInvokeResult result = client.invoke(req);

        assertThat(result.getOutputJson())
                .as("清洗后 outputJson 应不包含原始邮箱")
                .doesNotContain("test@example.com")
                .doesNotContain("13800138000")
                .contains("cleaned-content-no-pii");
    }

    @Test
    @DisplayName("8. callTool_recordsCallIdAndCacheFlag: 返回结果应包含 callId / fromCache")
    void callTool_recordsCallIdAndCacheFlag() {
        ToolInvokeRequest req = ToolInvokeRequest.builder()
                .callId("audit_008")
                .toolId("audited_tool")
                .build();

        ToolInvokeResult result = client.invoke(req);

        assertThat(result.getCallId())
                .as("callId 应等于请求 callId")
                .isEqualTo("audit_008");
        assertThat(result.isFromCache())
                .as("audit 场景应返回 fromCache=true")
                .isTrue();
    }

    @Test
    @DisplayName("9. callToolAsync_completesWithResult: invokeAsync 应正常完成并返回结果")
    void callToolAsync_completesWithResult() throws Exception {
        ToolInvokeRequest req = ToolInvokeRequest.builder()
                .callId("async_009")
                .toolId("async_tool")
                .inputJson("{\"x\":1}")
                .build();

        CompletableFuture<ToolInvokeResult> future = client.invokeAsync(req);

        ToolInvokeResult result = future.get(5, TimeUnit.SECONDS);
        assertThat(result)
                .as("async 调用应返回非 null 结果")
                .isNotNull();
        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getCallId()).isEqualTo("async_009");
    }
}
