package com.agent.runtime.api.impl;

import agentplatform.model.v1.ModelGatewayGrpc;
import com.agent.runtime.api.dto.ModelChatChunk;
import com.agent.runtime.api.dto.ModelChatRequest;
import com.agent.runtime.api.dto.ModelChatResponse;
import com.agent.runtime.api.dto.ModelMessage;
import com.agent.runtime.api.dto.ModelToolCall;
import com.agent.runtime.config.RuntimeProperties;
import com.agent.runtime.exception.ModelGatewayTimeoutException;
import com.agent.runtime.exception.ModelGatewayUnavailableException;
import com.agent.runtime.fixture.FakeModelGatewayService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * T4 ModelGatewayClientImpl 单元测试 (gRPC in-process server).
 *
 * <p>验证 {@link ModelGatewayClientImpl} 通过真实 gRPC 链路调用
 * {@link FakeModelGatewayService} 的 9 个场景:
 * <ol>
 *   <li>{@code chat_returnsCompletion} — 正常 prompt → 返回模型回复文本</li>
 *   <li>{@code chat_stream_returnsChunks} — stream 模式 → 聚合多个 chunk</li>
 *   <li>{@code chat_includesSystemAndUserMessages} — 验证请求体包含 system + user 消息</li>
 *   <li>{@code chat_passesTemperatureAndMaxTokens} — 验证 temperature / maxTokens 参数传递</li>
 *   <li>{@code chat_throwsOnUnavailable} — UNAVAILABLE → {@link ModelGatewayUnavailableException}</li>
 *   <li>{@code chat_throwsOnDeadlineExceeded} — DEADLINE_EXCEEDED → {@link ModelGatewayTimeoutException}</li>
 *   <li>{@code chat_returnsToolCallDecision} — tool_call → 解析 {@link ModelToolCall}</li>
 *   <li>{@code chat_returnsFinalAnswer} — final_answer 内容</li>
 *   <li>{@code chat_recordsTokenUsage} — 返回 token usage (prompt / completion / total)</li>
 * </ol>
 *
 * <p>基础设施: {@link InProcessServerBuilder} + {@link InProcessChannelBuilder}, directExecutor,
 * 无真实端口、无 Spring 上下文、无 Docker, 启动开销极小。
 */
class ModelGatewayClientImplTest {

    private static Server grpcServer;
    private static ManagedChannel channel;
    private static ModelGatewayClientImpl client;
    private static FakeModelGatewayService fakeService;

    @BeforeAll
    static void setUp() throws Exception {
        fakeService = new FakeModelGatewayService();
        String serverName = InProcessServerBuilder.generateName();
        grpcServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(fakeService)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        ModelGatewayGrpc.ModelGatewayBlockingStub stub =
                ModelGatewayGrpc.newBlockingStub(channel);
        client = new ModelGatewayClientImpl(stub, new RuntimeProperties());
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
    @DisplayName("1. chat_returnsCompletion: 正常 prompt 应返回模型回复文本")
    void chat_returnsCompletion() {
        ModelChatRequest req = ModelChatRequest.builder()
                .callId("normal_001")
                .scene("intent")
                .tier("middle")
                .userMessage("hello world")
                .build();

        ModelChatResponse resp = client.chat(req);

        assertThat(resp)
                .as("非 null 响应")
                .isNotNull();
        assertThat(resp.getContent())
                .as("content 应镜像 user 消息")
                .isEqualTo("hello world");
        assertThat(resp.getModel())
                .as("model 字段应为 fake-model")
                .isEqualTo("fake-model");
    }

    @Test
    @DisplayName("2. chat_stream_returnsChunks: stream 模式应聚合多个 chunk 返回完整文本")
    void chat_stream_returnsChunks() {
        ModelChatRequest req = ModelChatRequest.builder()
                .callId("stream_002")
                .userMessage("abcdefgh")
                .build();

        List<ModelChatChunk> chunks = client.chatStream(req).collect(Collectors.toList());

        assertThat(chunks)
                .as("应收到 3 个 chunk (2 delta + 1 finish)")
                .hasSize(3);
        String aggregated = chunks.stream()
                .map(ModelChatChunk::getDelta)
                .reduce("", String::concat);
        assertThat(aggregated)
                .as("聚合后应等于完整文本")
                .isEqualTo("abcdefgh");
        assertThat(chunks.get(2).isFinished())
                .as("最后一个 chunk 应标记为 STOP")
                .isTrue();
        assertThat(chunks.get(2).getFinish())
                .as("finish reason 应为 STOP")
                .isEqualTo(ModelChatChunk.FinishReason.STOP);
    }

    @Test
    @DisplayName("3. chat_includesSystemAndUserMessages: 请求体应包含 system + user 消息")
    void chat_includesSystemAndUserMessages() {
        ModelChatRequest req = ModelChatRequest.builder()
                .callId("normal_003")
                .systemMessage("You are a helpful assistant.")
                .userMessage("What is 1+1?")
                .build();

        client.chat(req);

        agentplatform.model.v1.ChatRequest captured = fakeService.getLastRequest();
        assertThat(captured)
                .as("fake service 应捕获到请求")
                .isNotNull();
        assertThat(captured.getMessagesList())
                .as("应包含 2 条消息")
                .hasSize(2);
        assertThat(captured.getMessages(0).getRole())
                .as("第一条 role 应为 system")
                .isEqualTo("system");
        assertThat(captured.getMessages(0).getContent())
                .as("system 消息内容应正确")
                .isEqualTo("You are a helpful assistant.");
        assertThat(captured.getMessages(1).getRole())
                .as("第二条 role 应为 user")
                .isEqualTo("user");
        assertThat(captured.getMessages(1).getContent())
                .as("user 消息内容应正确")
                .isEqualTo("What is 1+1?");
    }

    @Test
    @DisplayName("4. chat_passesTemperatureAndMaxTokens: 应正确传递 temperature / maxTokens 参数")
    void chat_passesTemperatureAndMaxTokens() {
        ModelChatRequest req = ModelChatRequest.builder()
                .callId("normal_004")
                .userMessage("test")
                .temperature(0.1)
                .maxTokens(256)
                .topP(0.95)
                .build();

        client.chat(req);

        agentplatform.model.v1.ChatRequest captured = fakeService.getLastRequest();
        assertThat(captured.getParams().getTemperature())
                .as("temperature 应为 0.1")
                .isEqualTo(0.1);
        assertThat(captured.getParams().getMaxTokens())
                .as("maxTokens 应为 256")
                .isEqualTo(256);
        assertThat(captured.getParams().getTopP())
                .as("topP 应为 0.95")
                .isEqualTo(0.95);
    }

    @Test
    @DisplayName("5. chat_throwsOnUnavailable: gRPC UNAVAILABLE 应抛 ModelGatewayUnavailableException")
    void chat_throwsOnUnavailable() {
        ModelChatRequest req = ModelChatRequest.builder()
                .callId("unavailable_005")
                .userMessage("test")
                .build();

        ModelGatewayUnavailableException ex = catchThrowableOfType(
                () -> client.chat(req),
                ModelGatewayUnavailableException.class);

        assertThat(ex)
                .as("UNAVAILABLE 应翻译为 ModelGatewayUnavailableException")
                .isNotNull();
        assertThat(ex.getMessage())
                .as("异常消息应包含 callId")
                .contains("unavailable_005");
    }

    @Test
    @DisplayName("6. chat_throwsOnDeadlineExceeded: gRPC DEADLINE_EXCEEDED 应抛 ModelGatewayTimeoutException")
    void chat_throwsOnDeadlineExceeded() {
        ModelChatRequest req = ModelChatRequest.builder()
                .callId("timeout_006")
                .userMessage("test")
                .build();

        ModelGatewayTimeoutException ex = catchThrowableOfType(
                () -> client.chat(req),
                ModelGatewayTimeoutException.class);

        assertThat(ex)
                .as("DEADLINE_EXCEEDED 应翻译为 ModelGatewayTimeoutException")
                .isNotNull();
        assertThat(ex.getMessage())
                .as("异常消息应包含 callId")
                .contains("timeout_006");
    }

    @Test
    @DisplayName("7. chat_returnsToolCallDecision: 模型返回 tool_call 应解析为 ModelToolCall")
    void chat_returnsToolCallDecision() {
        ModelChatRequest req = ModelChatRequest.builder()
                .callId("toolcall_007")
                .userMessage("search for x")
                .build();

        ModelChatResponse resp = client.chat(req);

        assertThat(resp.hasToolCalls())
                .as("响应应包含 tool_calls")
                .isTrue();
        ModelToolCall tc = resp.firstToolCall();
        assertThat(tc)
                .as("第一个 tool_call 非 null")
                .isNotNull();
        assertThat(tc.getToolId())
                .as("toolId 应为 t0")
                .isEqualTo("t0");
        assertThat(tc.getInputJson())
                .as("inputJson 应为 {\"q\":\"x\"}")
                .isEqualTo("{\"q\":\"x\"}");
    }

    @Test
    @DisplayName("8. chat_returnsFinalAnswer: 模型返回 final_answer 应作为 content 返回")
    void chat_returnsFinalAnswer() {
        ModelChatRequest req = ModelChatRequest.builder()
                .callId("final_008")
                .userMessage("any question")
                .build();

        ModelChatResponse resp = client.chat(req);

        assertThat(resp.getContent())
                .as("content 应为 final_answer:done")
                .isEqualTo("final_answer:done");
        assertThat(resp.hasToolCalls())
                .as("final answer 不应包含 tool_calls")
                .isFalse();
    }

    @Test
    @DisplayName("9. chat_recordsTokenUsage: 响应应包含 token usage (prompt / completion / total)")
    void chat_recordsTokenUsage() {
        ModelChatRequest req = ModelChatRequest.builder()
                .callId("tokens_009")
                .userMessage("count tokens")
                .build();

        ModelChatResponse resp = client.chat(req);

        assertThat(resp.getTokenUsage())
                .as("tokenUsage 非 null")
                .isNotNull();
        assertThat(resp.getTokenUsage().getPromptTokens())
                .as("promptTokens 应为 100")
                .isEqualTo(100);
        assertThat(resp.getTokenUsage().getCompletionTokens())
                .as("completionTokens 应为 50")
                .isEqualTo(50);
        assertThat(resp.getTokenUsage().getTotalTokens())
                .as("totalTokens 应为 150")
                .isEqualTo(150);
    }
}
