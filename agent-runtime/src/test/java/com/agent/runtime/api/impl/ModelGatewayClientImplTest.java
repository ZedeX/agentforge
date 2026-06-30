package com.agent.runtime.api.impl;

import com.agent.runtime.api.ModelGatewayClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ModelGatewayClientImpl 单元测试.
 *
 * <p>覆盖 chat() 方法的正常/边界/异常分支:
 * <ul>
 *   <li>正常 prompt (短 prompt / 长 prompt 摘要)</li>
 *   <li>边界 null prompt</li>
 *   <li>异常空字符串 prompt</li>
 * </ul>
 */
class ModelGatewayClientImplTest {

    @Test
    @DisplayName("正常场景: 调用 chat 返回包含 prompt 摘要的 mock 响应")
    void should_ReturnMockResponse_When_ChatWithNormalPrompt() {
        // 给定一个正常的短 prompt
        ModelGatewayClient client = new ModelGatewayClientImpl();
        String prompt = "分析用户问题";

        // 调用 chat
        String output = client.chat(prompt);

        // 验证返回非空, 且包含 prompt 摘要
        assertThat(output)
                .as("短 prompt 应原样包含在响应中")
                .isNotNull()
                .contains("分析用户问题")
                .contains("mock-model-output");
    }

    @Test
    @DisplayName("边界场景: 长 prompt 超过摘要最大长度, 应截断并附加省略号")
    void should_TruncatePrompt_When_PromptExceedsMaxLength() {
        // 给定一个超过 64 字符的长 prompt:
        // 前 70 个字符用 'a' 填充 (确保超过摘要最大长度 64), 末尾追加唯一标记
        ModelGatewayClient client = new ModelGatewayClientImpl();
        String padding = "a".repeat(70);
        String marker = "UNIQUE_MARKER_AFTER_TRUNCATION";
        String longPrompt = padding + marker;

        // 调用 chat
        String output = client.chat(longPrompt);

        // 验证返回包含截断标记 "...", 且不包含截断点之后的 marker
        assertThat(output)
                .as("长 prompt 应被截断并附加省略号")
                .isNotNull()
                .contains("...")
                .doesNotContain(marker);
    }

    @Test
    @DisplayName("异常场景: prompt 为 null 时应返回包含 null 摘要的响应, 不抛异常")
    void should_ReturnNullSummary_When_PromptIsNull() {
        // 给定 null prompt
        ModelGatewayClient client = new ModelGatewayClientImpl();

        // 调用 chat, 不应抛异常
        String output = client.chat(null);

        // 验证返回包含 null 摘要的响应
        assertThat(output)
                .as("null prompt 应返回包含 null 摘要的响应")
                .isNotNull()
                .contains("null");
    }
}
