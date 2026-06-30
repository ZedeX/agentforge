package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.AdapterContext;
import com.agent.modelgateway.model.ChatReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepSeekAdapterImpl unit tests.
 */
@DisplayName("DeepSeekAdapterImpl DeepSeek 适配器")
class DeepSeekAdapterImplTest {

    private final DeepSeekAdapterImpl adapter = new DeepSeekAdapterImpl();

    @Test
    @DisplayName("getProviderCode 返回 deepseek")
    void should_ReturnDeepSeekCode_When_Called() {
        assertThat(adapter.getProviderCode()).isEqualTo("deepseek");
    }

    @Test
    @DisplayName("chat 返回包含 prompt 前缀的 mock 响应")
    void should_ReturnMockResponse_When_ChatCalled() {
        AdapterContext ctx = new AdapterContext("trace-1", "tenant-1", Scene.INTENT, 30000, false);
        ChatReply reply = adapter.chat(ctx, "What is the intent here?");
        assertThat(reply).isNotNull();
        assertThat(reply.getProviderCode()).isEqualTo("deepseek");
        assertThat(reply.getModelName()).isEqualTo("deepseek-v3");
        assertThat(reply.getContent()).contains("What is the intent");
        assertThat(reply.getInputTokens()).isPositive();
        assertThat(reply.getOutputTokens()).isPositive();
        assertThat(reply.isCacheHit()).isFalse();
    }

    @Test
    @DisplayName("null prompt 安全处理为空串")
    void should_HandleNullPrompt_When_ChatCalled() {
        AdapterContext ctx = new AdapterContext("trace-2", "tenant-1", Scene.INTENT, 30000, false);
        ChatReply reply = adapter.chat(ctx, null);
        assertThat(reply).isNotNull();
        assertThat(reply.getContent()).startsWith("[deepseek:");
    }

    @Test
    @DisplayName("health 始终返回 true (mock)")
    void should_ReturnTrue_When_HealthChecked() {
        assertThat(adapter.health()).isTrue();
    }
}
