package com.agent.riskcontrol.api.impl;

import com.agent.riskcontrol.api.ComplianceAuditor;
import com.agent.riskcontrol.config.RiskControlProperties;
import com.agent.riskcontrol.entity.AuditLogEntity;
import com.agent.riskcontrol.exception.AuditException;
import com.agent.riskcontrol.model.AuditLogAck;
import com.agent.riskcontrol.model.AuditLogRequest;
import com.agent.riskcontrol.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Compliance auditor implementation.
 *
 * <p>Records audit log entries to JPA repository and returns an acknowledgment
 * with a generated audit_id.
 */
@Slf4j
@Component
public class ComplianceAuditorImpl implements ComplianceAuditor {

    private final RiskControlProperties properties;
    private final AuditLogRepository auditLogRepository;

    public ComplianceAuditorImpl(RiskControlProperties properties,
                                 AuditLogRepository auditLogRepository) {
        this.properties = properties;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public AuditLogAck record(AuditLogRequest request) {
        if (!properties.getAudit().isEnabled()) {
            log.debug("Audit logging is disabled, skipping record");
            return new AuditLogAck("audit-disabled");
        }

        if (request.getAction() == null || request.getAction().isEmpty()) {
            throw new AuditException("Audit action must not be empty");
        }

        try {
            String auditId = generateAuditId();

            AuditLogEntity entity = new AuditLogEntity();
            entity.setAuditId(auditId);
            entity.setAction(request.getAction());
            entity.setActorId(request.getActorId());
            entity.setResourceType(request.getResourceType());
            entity.setResourceId(request.getResourceId());
            entity.setResult(request.getResult());
            entity.setDetail(request.getDetail());
            entity.setCreatedAt(Instant.now());

            auditLogRepository.save(entity);

            log.info("Audit log recorded: auditId={} action={} actorId={} resource={}/{} result={}",
                    auditId, request.getAction(), request.getActorId(),
                    request.getResourceType(), request.getResourceId(), request.getResult());

            return new AuditLogAck(auditId);
        } catch (AuditException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage(), e);
            throw new AuditException("Failed to record audit log: " + e.getMessage(), e);
        }
    }

    private String generateAuditId() {
        return "audit-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
