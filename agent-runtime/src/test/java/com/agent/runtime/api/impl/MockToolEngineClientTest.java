package com.agent.runtime.api.impl;

import com.agent.runtime.api.ToolEngineClient;
import com.agent.runtime.api.dto.ToolInvokeRequest;
import com.agent.runtime.api.dto.ToolInvokeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MockToolEngineClient} 单元测试.
 *
 * <p>验证 fallback mock 实现的契约:
 * <ul>
 *   <li>{@code invoke(String, String)} 保留 T3 阶段的 mock JSON 行为 ({@code mock-result}).</li>
 *   <li>{@code invoke(ToolInvokeRequest)} 返回 status=success + 镜像 toolId 的 outputJson.</li>
 *   <li>{@code invokeAsync(ToolInvokeRequest)} 立即完成 future.</li>
 * </ul>
 */
class MockToolEngineClientTest {

    @Test
    @DisplayName("invoke(String, String): 应返回包含 toolId 和 mock-result 的 JSON")
    void should_ReturnMockJson_When_LegacyInvoke() {
        ToolEngineClient client = new MockToolEngineClient();

        String output = client.invoke("search", "{\"q\":\"x\"}");

        assertThat(output)
                .isNotNull()
                .contains("search")
                .contains(MockToolEngineClient.LEGACY_MOCK_RESULT);
    }

    @Test
    @DisplayName("invoke(ToolInvokeRequest): 应返回 status=success + 镜像 toolId 的 outputJson")
    void should_ReturnSuccessResult_When_StructuredInvoke() {
        ToolEngineClient client = new MockToolEngineClient();
        ToolInvokeRequest req = ToolInvokeRequest.builder()
                .callId("c1")
                .toolId("calculator")
                .inputJson("{\"a\":1}")
                .build();

        ToolInvokeResult result = client.invoke(req);

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getCallId()).isEqualTo("c1");
        assertThat(result.getOutputJson())
                .contains("calculator")
                .contains(MockToolEngineClient.LEGACY_MOCK_RESULT);
    }

    @Test
    @DisplayName("invokeAsync(ToolInvokeRequest): 应立即完成 future 并返回结果")
    void should_CompleteImmediately_When_AsyncInvoke() throws Exception {
        ToolEngineClient client = new MockToolEngineClient();
        ToolInvokeRequest req = ToolInvokeRequest.builder()
                .callId("c2")
                .toolId("t")
                .build();

        ToolInvokeResult result = client.invokeAsync(req).get();

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("success");
    }
}
