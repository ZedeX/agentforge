package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.AdapterContext;
import com.agent.modelgateway.model.ChatReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAiAdapterImpl unit tests.
 */
@DisplayName("OpenAiAdapterImpl OpenAI 适配器")
class OpenAiAdapterImplTest {

    private final OpenAiAdapterImpl adapter = new OpenAiAdapterImpl();

    @Test
    @DisplayName("getProviderCode 返回 openai")
    void should_ReturnOpenAiCode_When_Called() {
        assertThat(adapter.getProviderCode()).isEqualTo("openai");
    }

    @Test
    @DisplayName("chat 返回包含 prompt 前缀的 mock 响应")
    void should_ReturnMockResponse_When_ChatCalled() {
        AdapterContext ctx = new AdapterContext("trace-1", "tenant-1", Scene.GENERIC, 30000, false);
        ChatReply reply = adapter.chat(ctx, "Hello, how are you?");
        assertThat(reply).isNotNull();
        assertThat(reply.getProviderCode()).isEqualTo("openai");
        assertThat(reply.getModelName()).isEqualTo("gpt-4o");
        assertThat(reply.getContent()).contains("Hello");
        assertThat(reply.getInputTokens()).isPositive();
        assertThat(reply.getOutputTokens()).isPositive();
        assertThat(reply.isCacheHit()).isFalse();
    }

    @Test
    @DisplayName("null prompt 安全处理为空串")
    void should_HandleNullPrompt_When_ChatCalled() {
        AdapterContext ctx = new AdapterContext("trace-2", "tenant-1", Scene.GENERIC, 30000, false);
        ChatReply reply = adapter.chat(ctx, null);
        assertThat(reply).isNotNull();
        assertThat(reply.getContent()).startsWith("[openai:");
    }

    @Test
    @DisplayName("health 始终返回 true (mock)")
    void should_ReturnTrue_When_HealthChecked() {
        assertThat(adapter.health()).isTrue();
    }
}
