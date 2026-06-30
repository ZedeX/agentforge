package com.agent.hallucination.api.impl;

import com.agent.hallucination.enums.GuardResult;
import com.agent.hallucination.model.ToolCallGuardRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolGatewayGuardImpl} 单元测试。
 */
class ToolGatewayGuardImplTest {

    private final ToolGatewayGuardImpl guard = new ToolGatewayGuardImpl();

    @Test
    @DisplayName("必填字段齐备: 返回 ALLOWED")
    void shouldAllowWhenAllRequiredFieldsPresent() {
        ToolCallGuardRequest req = new ToolCallGuardRequest("search", Map.of("query", "agent", "limit", 5));
        req.setRequiredFields(List.of("query", "limit"));

        assertThat(guard.guard(req)).isEqualTo(GuardResult.ALLOWED);
    }

    @Test
    @DisplayName("必填字段缺失: 返回 REJECTED")
    void shouldRejectWhenRequiredFieldMissing() {
        ToolCallGuardRequest req = new ToolCallGuardRequest("search", Map.of("query", "agent"));
        req.setRequiredFields(List.of("query", "limit"));

        assertThat(guard.guard(req)).isEqualTo(GuardResult.REJECTED);
    }

    @Test
    @DisplayName("必填字段为空白字符串: 返回 REJECTED")
    void shouldRejectWhenRequiredFieldBlank() {
        ToolCallGuardRequest req = new ToolCallGuardRequest("search", Map.of("query", "   "));
        req.setRequiredFields(List.of("query"));

        assertThat(guard.guard(req)).isEqualTo(GuardResult.REJECTED);
    }

    @Test
    @DisplayName("未声明必填字段 / 空请求 / params 为 null")
    void shouldHandleEdgeCases() {
        ToolCallGuardRequest noRequired = new ToolCallGuardRequest("ping", Map.of());
        assertThat(guard.guard(noRequired)).isEqualTo(GuardResult.ALLOWED);
        assertThat(guard.guard(null)).isEqualTo(GuardResult.REJECTED);

        ToolCallGuardRequest nullParams = new ToolCallGuardRequest();
        nullParams.setToolId("search");
        nullParams.setParams(null);
        nullParams.setRequiredFields(List.of("query"));
        assertThat(guard.guard(nullParams)).isEqualTo(GuardResult.REJECTED);
    }

    @Test
    @DisplayName("必填字段列表含 null 项: 跳过 null, 其余齐备放行")
    void shouldSkipNullFieldEntry() {
        ToolCallGuardRequest req = new ToolCallGuardRequest("search", Map.of("query", "agent"));
        req.setRequiredFields(Arrays.asList(null, "query"));

        assertThat(guard.guard(req)).isEqualTo(GuardResult.ALLOWED);
    }
}