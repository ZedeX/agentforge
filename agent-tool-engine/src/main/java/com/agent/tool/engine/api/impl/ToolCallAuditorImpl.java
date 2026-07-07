package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ToolCallAuditor;
import com.agent.tool.engine.audit.AuditLogMapper;
import com.agent.tool.engine.entity.ToolCallLogEntity;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.model.ApprovalRecord;
import com.agent.tool.engine.model.RiskAssessment;
import com.agent.tool.engine.model.ToolCallAuditLog;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.repository.ToolCallLogRepository;
import com.agent.tool.engine.sandbox.SandboxInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * F8 工具调用审计实现 (T9: JPA 落库 + 查询接口).
 *
 * <p>Provides two persistence paths:
 * <ol>
 *   <li>{@link #audit(ToolCallAuditLog)} — legacy API used by {@code ToolGatewayImpl}
 *       (T8). Persists via the same JPA repository with {@code REQUIRES_NEW}
 *       transaction; audit failures now propagate to caller (S-12 de-swallow).</li>
 *   <li>{@link #record(ToolCallRequest, RiskAssessment, SandboxInstance,
 *       ToolCallResult, ApprovalRecord)} — T9 full-context API. Builds the
 *       16-field audit POJO via {@link AuditLogMapper#buildLog} then persists.</li>
 * </ol>
 * </p>
 *
 * <p>All write operations run in a new transaction
 * ({@code @Transactional(propagation=REQUIRES_NEW)}) so that audit entries
 * remain visible even when the caller's transaction rolls back — a key
 * compliance requirement for the F8 audit trail.</p>
 *
 * <p>An in-memory fallback list is retained for non-JPA test environments
 * where the {@link ToolCallLogRepository} bean is unavailable (e.g. plain
 * unit tests without {@code @DataJpaTest}). When the repository is null,
 * both {@code audit()} and {@code record()} fall back to in-memory storage
 * and the query methods return empty results.</p>
 */
@Component
public class ToolCallAuditorImpl implements ToolCallAuditor {

    private static final Logger log = LoggerFactory.getLogger(ToolCallAuditorImpl.class);

    private final ToolCallLogRepository repository;
    private final AuditLogMapper mapper;
    /** In-memory fallback (used when repository is null in non-JPA tests). */
    private final List<ToolCallAuditLog> memoryLogs = new ArrayList<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    /** Spring constructor: inject repository (required) + mapper (fallback to singleton). */
    public ToolCallAuditorImpl(ToolCallLogRepository repository) {
        this(repository, AuditLogMapper.INSTANCE);
    }

    /** Spring primary constructor: inject both repository and mapper beans. */
    @Autowired
    public ToolCallAuditorImpl(ToolCallLogRepository repository, AuditLogMapper mapper) {
        this.repository = repository;
        this.mapper = mapper != null ? mapper : AuditLogMapper.INSTANCE;
    }

    /**
     * No-arg constructor for non-Spring unit tests (e.g. {@code ToolGatewayImplTest}).
     *
     * <p>Sets {@code repository=null} so all writes go to the in-memory fallback
     * list and queries return empty results. Production code should never use
     * this constructor — Spring should always inject the JPA repository.</p>
     */
    public ToolCallAuditorImpl() {
        this(null, AuditLogMapper.INSTANCE);
    }

    // ==================== Write API ====================

    @Override
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(ToolCallAuditLog logEntry) {
        if (logEntry == null) {
            log.warn("审计收到 null 日志, 跳过");
            return;
        }
        if (logEntry.getLogId() == null || logEntry.getLogId().isBlank()) {
            logEntry.setLogId("log-" + idSeq.incrementAndGet());
        }
        if (logEntry.getOccurredAt() == null) {
            logEntry.setOccurredAt(Instant.now());
        }
        if (repository == null) {
            synchronized (memoryLogs) {
                memoryLogs.add(logEntry);
            }
            log.debug("内存审计日志写入: logId={}, toolId={}, status={}",
                    logEntry.getLogId(), logEntry.getToolId(), logEntry.getStatus());
            return;
        }
        try {
            ToolCallLogEntity entity = mapper.toEntity(logEntry);
            ToolCallLogEntity saved = repository.save(entity);
            logEntry.setLogId(saved.getId() != null ? String.valueOf(saved.getId()) : logEntry.getLogId());
            log.debug("JPA 审计日志写入: logId={}, callId={}, toolId={}, status={}",
                    logEntry.getLogId(), saved.getCallId(), saved.getToolId(), saved.getStatus());
        } catch (Exception e) {
            log.error("审计 JPA 落库失败: toolId={}, err={}", logEntry.getToolId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String record(ToolCallRequest request, RiskAssessment assessment,
                         SandboxInstance sandbox, ToolCallResult result,
                         ApprovalRecord approval) {
        if (request == null) {
            log.warn("record 收到 null request, 跳过");
            return null;
        }
        ToolCallAuditLog logEntry = mapper.buildLog(request, assessment, sandbox, result, approval);
        if (repository == null) {
            logEntry.setLogId("log-" + idSeq.incrementAndGet());
            synchronized (memoryLogs) {
                memoryLogs.add(logEntry);
            }
            log.debug("record 内存审计写入: logId={}, toolId={}, status={}",
                    logEntry.getLogId(), logEntry.getToolId(), logEntry.getStatus());
            return logEntry.getLogId();
        }
        try {
            ToolCallLogEntity entity = mapper.toEntity(logEntry);
            ToolCallLogEntity saved = repository.save(entity);
            String logId = saved.getId() != null ? String.valueOf(saved.getId()) : null;
            log.debug("record JPA 审计写入: logId={}, callId={}, toolId={}, status={}",
                    logId, saved.getCallId(), saved.getToolId(), saved.getStatus());
            return logId;
        } catch (Exception e) {
            log.error("record JPA 落库失败: toolId={}, err={}", request.getToolId(), e.getMessage(), e);
            throw e;
        }
    }

    // ==================== Query API ====================

    @Override
    public Optional<ToolCallAuditLog> findByCallId(String callId) {
        if (callId == null || callId.isBlank()) {
            return Optional.empty();
        }
        if (repository == null) {
            synchronized (memoryLogs) {
                return memoryLogs.stream()
                        .filter(l -> callId.equals(l.getCallId()) || callId.equals(l.getTraceId()))
                        .findFirst();
            }
        }
        return repository.findByCallId(callId).map(mapper::fromEntity);
    }

    @Override
    public Page<ToolCallAuditLog> findByTenantIdAndTimeRange(
            String tenantId, Instant from, Instant to, Pageable pageable) {
        if (repository == null) {
            return Page.empty(pageable);
        }
        return repository.findByTenantIdAndCreatedAtBetween(tenantId, from, to, pageable)
                .map(mapper::fromEntity);
    }

    @Override
    public Page<ToolCallAuditLog> findByTenantIdAndToolId(
            String tenantId, String toolId, Pageable pageable) {
        if (repository == null) {
            return Page.empty(pageable);
        }
        return repository.findByTenantIdAndToolId(tenantId, toolId, pageable)
                .map(mapper::fromEntity);
    }

    @Override
    public long countByStatus(String tenantId, ToolCallStatus status) {
        if (status == null) {
            return 0L;
        }
        if (repository == null) {
            synchronized (memoryLogs) {
                return memoryLogs.stream()
                        .filter(l -> status.equals(l.getStatus()))
                        .count();
            }
        }
        return repository.countByTenantIdAndStatus(tenantId, status.name());
    }

    // ==================== Test helpers (legacy) ====================

    /** 当前内存审计日志条数 (供测试 / 监控使用; 不计入 JPA 落库的条数). */
    public int count() {
        synchronized (memoryLogs) {
            return memoryLogs.size();
        }
    }

    /** 返回内存审计日志副本 (供测试 / 查询使用). */
    public List<ToolCallAuditLog> allLogs() {
        synchronized (memoryLogs) {
            return new ArrayList<>(memoryLogs);
        }
    }
}
