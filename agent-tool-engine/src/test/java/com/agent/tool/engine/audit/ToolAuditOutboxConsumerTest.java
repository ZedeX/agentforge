package com.agent.tool.engine.audit;

import com.agent.common.utils.JsonUtils;
import com.agent.tool.engine.api.ToolCallAuditor;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.model.ToolCallAuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * TDD tests for {@link ToolAuditOutboxConsumer}.
 *
 * <p>Validates that outbox-delivered audit messages are correctly
 * deserialized and persisted via {@link ToolCallAuditor#audit}.</p>
 */
@ExtendWith(MockitoExtension.class)
class ToolAuditOutboxConsumerTest {

    @Mock
    private ToolCallAuditor auditor;

    private ToolAuditOutboxConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ToolAuditOutboxConsumer(auditor);
    }

    @Test
    @DisplayName("consumer should deserialize audit log and call auditor.audit")
    void should_DeserializeAndAudit_When_ValidPayload() {
        ToolCallAuditLog log = new ToolCallAuditLog("trace-1", "tool_r1", ToolCallStatus.SUCCESS);
        log.setTenantId("tn_test");
        log.setInputJson("{\"q\":\"test\"}");
        String payload = JsonUtils.toJson(log);

        consumer.handle("tool.audit", payload);

        verify(auditor).audit(any(ToolCallAuditLog.class));
    }

    @Test
    @DisplayName("consumer should skip non-tool.audit topics")
    void should_Skip_When_WrongTopic() {
        consumer.handle("runtime.stepstate", "{}");

        verify(auditor, times(0)).audit(any());
    }

    @Test
    @DisplayName("consumer should throw when auditor.audit fails (triggering outbox retry)")
    void should_Throw_When_AuditorFails() {
        doThrow(new RuntimeException("audit DB down")).when(auditor).audit(any());

        ToolCallAuditLog log = new ToolCallAuditLog("trace-2", "tool_fail", ToolCallStatus.FAILED);
        String payload = JsonUtils.toJson(log);

        assertThatThrownBy(() -> consumer.handle("tool.audit", payload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("audit DB down");
    }

    @Test
    @DisplayName("consumer should throw on invalid JSON payload")
    void should_Throw_When_InvalidPayload() {
        assertThatThrownBy(() -> consumer.handle("tool.audit", "not-json"))
                .isInstanceOf(Exception.class);
    }
}
