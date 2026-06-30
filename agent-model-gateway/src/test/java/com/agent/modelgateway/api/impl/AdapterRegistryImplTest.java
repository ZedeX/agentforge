package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.api.ModelProviderAdapter;
import com.agent.modelgateway.model.AdapterContext;
import com.agent.modelgateway.model.ChatReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AdapterRegistryImpl unit tests.
 */
@DisplayName("AdapterRegistryImpl 适配器注册中心")
class AdapterRegistryImplTest {

    private final AdapterRegistryImpl registry = new AdapterRegistryImpl();

    @Test
    @DisplayName("注册后能按 providerCode 查找")
    void should_FindAdapter_When_Registered() {
        ModelProviderAdapter adapter = new StubAdapter("openai");
        registry.register(adapter);
        assertThat(registry.has("openai")).isTrue();
        assertThat(registry.get("openai")).isSameAs(adapter);
    }

    @Test
    @DisplayName("未注册的 providerCode 返回 null")
    void should_ReturnNull_When_NotRegistered() {
        assertThat(registry.has("anthropic")).isFalse();
        assertThat(registry.get("anthropic")).isNull();
    }

    @Test
    @DisplayName("null adapter 或 null providerCode 安全跳过")
    void should_SkipRegistration_When_AdapterOrCodeNull() {
        registry.register(null);
        registry.register(new StubAdapter(null));
        assertThat(registry.has(null)).isFalse();
    }

    @Test
    @DisplayName("重复注册同 providerCode 覆盖旧实例")
    void should_Overwrite_When_ReRegistered() {
        ModelProviderAdapter a1 = new StubAdapter("openai");
        ModelProviderAdapter a2 = new StubAdapter("openai");
        registry.register(a1);
        registry.register(a2);
        assertThat(registry.get("openai")).isSameAs(a2);
    }

    /** Minimal stub adapter for testing. */
    private static class StubAdapter implements ModelProviderAdapter {
        private final String code;
        StubAdapter(String code) { this.code = code; }
        @Override public String getProviderCode() { return code; }
        @Override public ChatReply chat(AdapterContext context, String prompt) { return null; }
        @Override public boolean health() { return true; }
    }
}
