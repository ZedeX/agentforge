package com.agent.tool.engine.audit;

import com.agent.tool.engine.entity.ToolCallLogEntity;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.model.ApprovalRecord;
import com.agent.tool.engine.model.RiskAssessment;
import com.agent.tool.engine.model.ToolCallAuditLog;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.sandbox.SandboxInstance;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Maps between {@link ToolCallAuditLog} POJO and {@link ToolCallLogEntity} JPA
 * entity, and constructs the audit POJO from the full call context (T9).
 *
 * <p>Centralising the mapping here keeps {@code ToolCallAuditorImpl} focused
 * on persistence + query orchestration while making the field assembly
 * testable in isolation.</p>
 */
@Component
public final class AuditLogMapper {

    /** Singleton instance for non-Spring callers (tests). */
    public static final AuditLogMapper INSTANCE = new AuditLogMapper();

    /**
     * Build a {@link ToolCallAuditLog} from the full call context.
     *
     * <p>Fills all 16 T9 audit fields plus the legacy fields (traceId, inputJson,
     * output, errorStack, occurredAt, tenantId) so the resulting POJO is
     * compatible with both the new {@code record(...)} API and the legacy
     * {@code audit(ToolCallAuditLog)} API.</p>
     */
    public ToolCallAuditLog buildLog(ToolCallRequest request,
                                     RiskAssessment assessment,
                                     SandboxInstance sandbox,
                                     ToolCallResult result,
                                     ApprovalRecord approval) {
        Instant now = Instant.now();
        ToolCallAuditLog logEntry = new ToolCallAuditLog();

        // Legacy fields
        logEntry.setTraceId(request.getTraceId());
        logEntry.setToolId(request.getToolId());
        logEntry.setInputJson(request.getInputJson());
        logEntry.setOccurredAt(now);
        logEntry.setTenantId(request.getTenantId());

        // T9 field 1: callId — prefer traceId, fallback to dedicated callId
        logEntry.setCallId(request.getTraceId());
        // T9 field 2: tenantId (already set above)
        // T9 field 3: agentId
        logEntry.setAgentId(request.getAgentId());
        // T9 field 4: toolId (already set above)
        // T9 field 5: paramsHash
        logEntry.setParamsHash(request.getInputHash());
        // T9 field 6: toolVersion (default 1 if not provided)
        logEntry.setToolVersion(1);
        // T9 field 7: riskLevel
        if (assessment != null && assessment.getRiskLevel() != null) {
            logEntry.setRiskLevel(assessment.getRiskLevel());
        }
        // T9 field 8: startedAt
        logEntry.setStartedAt(now);
        // T9 field 9: endedAt
        logEntry.setEndedAt(now);
        // T9 field 10: durationMs (default 0; caller can override)
        logEntry.setDurationMs(0L);
        // T9 field 11: costTokens
        if (result != null) {
            logEntry.setCostTokens(result.getOutputTokens());
        }
        // T9 field 12: exitCode (null for non-sandbox)
        // T9 field 13: errorMessage
        if (result != null) {
            logEntry.setErrorMessage(result.getErrorStack());
            logEntry.setErrorStack(result.getErrorStack());
            logEntry.setOutput(result.getOutput());
        }
        // T9 field 14: sandboxContainerId
        if (sandbox != null) {
            logEntry.setSandboxContainerId(sandbox.getContainerId());
        }
        // T9 field 15: approverId
        if (approval != null) {
            logEntry.setApproverId(approval.getPrimaryApprover());
        }
        // T9 field 16: cacheHit
        if (result != null) {
            logEntry.setCacheHit(result.isFromCache());
        }
        // Status
        if (result != null && result.getStatus() != null) {
            logEntry.setStatus(result.getStatus());
        } else {
            logEntry.setStatus(ToolCallStatus.FAILED);
        }
        // Task id (shard key)
        logEntry.setTaskId(request.getTaskId() != null ? request.getTaskId() : "unknown");
        return logEntry;
    }

    /** Convert {@link ToolCallAuditLog} POJO → {@link ToolCallLogEntity} for JPA persistence. */
    public ToolCallLogEntity toEntity(ToolCallAuditLog log) {
        if (log == null) {
            return null;
        }
        ToolCallLogEntity entity = new ToolCallLogEntity();
        entity.setCallId(safe(log.getCallId() != null ? log.getCallId() : log.getTraceId(),
                "call-" + System.nanoTime()));
        entity.setTaskId(safe(log.getTaskId(), "unknown"));
        entity.setAgentId(log.getAgentId() != null ? log.getAgentId() : 0L);
        entity.setToolId(safe(log.getToolId(), "unknown"));
        entity.setToolVersion(log.getToolVersion());
        entity.setInput(safe(log.getInputJson(), "{}"));
        entity.setOutput(log.getOutput());
        entity.setStatus(safe(log.getStatus() != null ? log.getStatus().name() : "FAILED", "FAILED"));
        entity.setErrorCode(null);
        entity.setErrorMsg(log.getErrorMessage() != null ? log.getErrorMessage() : log.getErrorStack());
        entity.setDurationMs((int) Math.max(0L, log.getDurationMs()));
        entity.setCostCent(0L);
        entity.setTokenUsed(log.getCostTokens());
        entity.setRiskLevel(riskLevelToInt(log.getRiskLevel()));
        entity.setApprovedBy(log.getApproverId());
        entity.setTraceId(safe(log.getTraceId() != null ? log.getTraceId() : log.getCallId(),
                "trace-" + System.nanoTime()));
        entity.setTenantId(log.getTenantId());
        entity.setParamsHash(log.getParamsHash());
        entity.setStartedAt(log.getStartedAt());
        entity.setEndedAt(log.getEndedAt());
        entity.setExitCode(log.getExitCode());
        entity.setSandboxContainerId(log.getSandboxContainerId());
        entity.setCacheHit(log.isCacheHit());
        if (log.getStepNo() != null) {
            entity.setStepNo(log.getStepNo());
        }
        return entity;
    }

    /** Convert {@link ToolCallLogEntity} JPA entity → {@link ToolCallAuditLog} POJO. */
    public ToolCallAuditLog fromEntity(ToolCallLogEntity entity) {
        if (entity == null) {
            return null;
        }
        ToolCallAuditLog log = new ToolCallAuditLog();
        log.setLogId(entity.getId() != null ? String.valueOf(entity.getId()) : null);
        log.setCallId(entity.getCallId());
        log.setTaskId(entity.getTaskId());
        log.setAgentId(entity.getAgentId());
        log.setToolId(entity.getToolId());
        log.setToolVersion(entity.getToolVersion());
        log.setInputJson(entity.getInput());
        log.setOutput(entity.getOutput());
        try {
            log.setStatus(ToolCallStatus.valueOf(entity.getStatus()));
        } catch (IllegalArgumentException e) {
            log.setStatus(ToolCallStatus.FAILED);
        }
        log.setErrorStack(entity.getErrorMsg());
        log.setErrorMessage(entity.getErrorMsg());
        log.setDurationMs(entity.getDurationMs());
        log.setCostTokens(entity.getTokenUsed());
        log.setRiskLevel(intToRiskLevel(entity.getRiskLevel()));
        log.setApproverId(entity.getApprovedBy());
        log.setTraceId(entity.getTraceId());
        log.setTenantId(entity.getTenantId());
        log.setParamsHash(entity.getParamsHash());
        log.setStartedAt(entity.getStartedAt());
        log.setEndedAt(entity.getEndedAt());
        log.setExitCode(entity.getExitCode());
        log.setSandboxContainerId(entity.getSandboxContainerId());
        log.setCacheHit(entity.isCacheHit());
        log.setOccurredAt(entity.getCreatedAt());
        log.setStepNo(entity.getStepNo());
        return log;
    }

    private static String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static int riskLevelToInt(ToolRiskLevel level) {
        return level == null ? 0 : level.getLevel();
    }

    private static ToolRiskLevel intToRiskLevel(int value) {
        try {
            return ToolRiskLevel.fromLevel(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
