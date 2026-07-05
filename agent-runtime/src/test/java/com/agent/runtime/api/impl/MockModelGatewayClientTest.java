package com.agent.runtime.api.impl;

import com.agent.runtime.api.ModelGatewayClient;
import com.agent.runtime.api.dto.ModelChatChunk;
import com.agent.runtime.api.dto.ModelChatRequest;
import com.agent.runtime.api.dto.ModelChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MockModelGatewayClient} 单元测试.
 *
 * <p>验证 fallback mock 实现的契约:
 * <ul>
 *   <li>{@code chat(String)} 保留 T1/T3 阶段的 mock JSON 行为 ({@code mock-model-output}).</li>
 *   <li>{@code chat(ModelChatRequest)} 返回镜像 user 消息的 content + zero token usage.</li>
 *   <li>{@code chatStream(ModelChatRequest)} 发出 1 个 delta + 1 个 STOP chunk.</li>
 *   <li>长 prompt 截断 + null prompt 不抛异常.</li>
 * </ul>
 */
class MockModelGatewayClientTest {

    @Test
    @DisplayName("正常场景: 调用 chat(String) 返回包含 prompt 摘要的 mock 响应")
    void should_ReturnMockResponse_When_ChatWithNormalPrompt() {
        ModelGatewayClient client = new MockModelGatewayClient();
        String prompt = "分析用户问题";

        String output = client.chat(prompt);

        assertThat(output)
                .as("短 prompt 应原样包含在响应中")
                .isNotNull()
                .contains("分析用户问题")
                .contains(MockModelGatewayClient.LEGACY_MOCK_OUTPUT);
    }

    @Test
    @DisplayName("边界场景: 长 prompt 超过摘要最大长度, 应截断并附加省略号")
    void should_TruncatePrompt_When_PromptExceedsMaxLength() {
        ModelGatewayClient client = new MockModelGatewayClient();
        String padding = "a".repeat(70);
        String marker = "UNIQUE_MARKER_AFTER_TRUNCATION";
        String longPrompt = padding + marker;

        String output = client.chat(longPrompt);

        assertThat(output)
                .as("长 prompt 应被截断并附加省略号")
                .isNotNull()
                .contains("...")
                .doesNotContain(marker);
    }

    @Test
    @DisplayName("异常场景: prompt 为 null 时应返回包含 null 摘要的响应, 不抛异常")
    void should_ReturnNullSummary_When_PromptIsNull() {
        ModelGatewayClient client = new MockModelGatewayClient();

        String output = client.chat((String) null);

        assertThat(output)
                .as("null prompt 应返回包含 null 摘要的响应")
                .isNotNull()
                .contains("null");
    }

    @Test
    @DisplayName("chat(ModelChatRequest): 应镜像第一条 user 消息作为 content")
    void should_MirrorFirstUserMessage_When_StructuredChat() {
        ModelGatewayClient client = new MockModelGatewayClient();
        ModelChatRequest req = ModelChatRequest.builder()
                .callId("c1")
                .systemMessage("sys")
                .userMessage("hello")
                .build();

        ModelChatResponse resp = client.chat(req);

        assertThat(resp.getContent()).isEqualTo("hello");
        assertThat(resp.getModel()).isEqualTo("mock-model");
        assertThat(resp.isCacheHit()).isTrue();
        assertThat(resp.getTokenUsage().getTotalTokens()).isZero();
    }

    @Test
    @DisplayName("chatStream(ModelChatRequest): 应发出 1 个 delta + 1 个 STOP chunk")
    void should_EmitDeltaAndStopChunk_When_StreamChat() {
        ModelGatewayClient client = new MockModelGatewayClient();
        ModelChatRequest req = ModelChatRequest.builder()
                .callId("c2")
                .userMessage("stream-me")
                .build();

        List<ModelChatChunk> chunks = client.chatStream(req).collect(Collectors.toList());

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getDelta()).isEqualTo("stream-me");
        assertThat(chunks.get(1).isFinished()).isTrue();
        assertThat(chunks.get(1).getFinish()).isEqualTo(ModelChatChunk.FinishReason.STOP);
    }
}
