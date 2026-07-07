package com.agent.tool.engine.audit;

import com.agent.common.outbox.OutboxMessageHandler;
import com.agent.common.utils.JsonUtils;
import com.agent.tool.engine.api.ToolCallAuditor;
import com.agent.tool.engine.model.ToolCallAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * S-04: Consumes outbox audit messages and persists them via ToolCallAuditor.
 *
 * <p>When the Outbox Relay delivers a "tool.audit" topic message via RocketMQ,
 * this handler deserializes the payload and calls {@link ToolCallAuditor#audit}.
 * The consumer is idempotent (deduplication happens at the OutboxConsumer level
 * via consume_log).</p>
 *
 * <p>If audit persistence fails, the consumer returns false so RocketMQ
 * retries delivery. This ensures eventual consistency: audit records are
 * never lost even if the audit DB is temporarily down.</p>
 */
@Component
public class ToolAuditOutboxConsumer implements OutboxMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(ToolAuditOutboxConsumer.class);

    private final ToolCallAuditor auditor;

    public ToolAuditOutboxConsumer(ToolCallAuditor auditor) {
        this.auditor = auditor;
    }

    @Override
    public void handle(String topic, String payload) {
        if (!"tool.audit".equals(topic)) {
            log.warn("ToolAuditOutboxConsumer received unexpected topic: {}", topic);
            return;
        }

        try {
            ToolCallAuditLog auditLog = JsonUtils.fromJson(payload, ToolCallAuditLog.class);
            auditor.audit(auditLog);
            log.debug("Outbox audit consumed: traceId={}, toolId={}, status={}",
                    auditLog.getTraceId(), auditLog.getToolId(), auditLog.getStatus());
        } catch (Exception e) {
            log.error("Outbox audit consumption failed: err={}", e.getMessage(), e);
            throw e; // Let OutboxConsumer catch and return false for retry
        }
    }
}
