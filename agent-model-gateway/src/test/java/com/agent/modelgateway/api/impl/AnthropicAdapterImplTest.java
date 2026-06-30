package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.AdapterContext;
import com.agent.modelgateway.model.ChatReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnthropicAdapterImpl unit tests.
 */
@DisplayName("AnthropicAdapterImpl Anthropic 适配器")
class AnthropicAdapterImplTest {

    private final AnthropicAdapterImpl adapter = new AnthropicAdapterImpl();

    @Test
    @DisplayName("getProviderCode 返回 anthropic")
    void should_ReturnAnthropicCode_When_Called() {
        assertThat(adapter.getProviderCode()).isEqualTo("anthropic");
    }

    @Test
    @DisplayName("chat 返回包含 prompt 前缀的 mock 响应")
    void should_ReturnMockResponse_When_ChatCalled() {
        AdapterContext ctx = new AdapterContext("trace-1", "tenant-1", Scene.AUDIT, 30000, false);
        ChatReply reply = adapter.chat(ctx, "Review this code carefully.");
        assertThat(reply).isNotNull();
        assertThat(reply.getProviderCode()).isEqualTo("anthropic");
        assertThat(reply.getModelName()).isEqualTo("claude-opus-4");
        assertThat(reply.getContent()).contains("Review this code");
        assertThat(reply.getInputTokens()).isPositive();
        assertThat(reply.getOutputTokens()).isPositive();
        assertThat(reply.isCacheHit()).isFalse();
    }

    @Test
    @DisplayName("null prompt 安全处理为空串")
    void should_HandleNullPrompt_When_ChatCalled() {
        AdapterContext ctx = new AdapterContext("trace-2", "tenant-1", Scene.AUDIT, 30000, false);
        ChatReply reply = adapter.chat(ctx, null);
        assertThat(reply).isNotNull();
        assertThat(reply.getContent()).startsWith("[anthropic:");
    }

    @Test
    @DisplayName("health 始终返回 true (mock)")
    void should_ReturnTrue_When_HealthChecked() {
        assertThat(adapter.health()).isTrue();
    }
}
