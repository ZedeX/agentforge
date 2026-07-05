package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ApprovalStore;
import com.agent.tool.engine.approval.ApprovalWaiter;
import com.agent.tool.engine.entity.ToolApprovalEntity;
import com.agent.tool.engine.enums.ApprovalDecision;
import com.agent.tool.engine.enums.ApprovalStatus;
import com.agent.tool.engine.exception.ToolApprovalException;
import com.agent.tool.engine.model.ApprovalRecord;
import com.agent.tool.engine.model.ApprovalRequest;
import com.agent.tool.engine.repository.ToolApprovalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * F8 / doc 05-tool-engine §4.3 / §4.4 approval store JPA implementation.
 *
 * <p>Persists approval records to {@code tool_approval} table via
 * {@link ToolApprovalRepository}, coordinates awaiting via
 * {@link ApprovalWaiter} (in-process CompletableFuture registry; production
 * may swap in a Redis pub/sub backed implementation for cross-replica await).</p>
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #submit}: persist PENDING entity, return approvalId</li>
 *   <li>{@link #await}: block on ApprovalWaiter until decision or SLA</li>
 *   <li>{@link #approve} / {@link #reject}: update entity status + notify waiter</li>
 *   <li>{@link #cleanupExpired}: scheduled sweep of stale PENDING → EXPIRED</li>
 * </ol>
 * </p>
 *
 * <p>Backward-compat: {@link #save(ApprovalRecord)} retained as a test/ops
 * helper to upsert an ApprovalRecord directly (used by ToolGatewayImplTest
 * to seed approved state without going through submit+approve).</p>
 */
@Component
public class ApprovalStoreImpl implements ApprovalStore {

    private static final Logger log = LoggerFactory.getLogger(ApprovalStoreImpl.class);

    /** Default SLA when request.sla is null: R3 = 30min (doc 05 §4.4). */
    private static final Duration DEFAULT_SLA = Duration.ofMinutes(30);
    /** Minimum SLA floor to avoid accidental 0/negative timeouts. */
    private static final Duration MIN_SLA = Duration.ofSeconds(1);

    private final ToolApprovalRepository repository;
    private final ApprovalWaiter waiter;

    private final AtomicLong fallbackIdSeq = new AtomicLong(0);

    public ApprovalStoreImpl(ToolApprovalRepository repository, ApprovalWaiter waiter) {
        this.repository = repository;
        this.waiter = waiter;
    }

    // ==================== Gateway / Classifier lookups ====================

    @Override
    public Optional<ApprovalRecord> findValid(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        List<ToolApprovalEntity> entities = repository
                .findByToolIdAndStatusAndExpireAtAfter(toolId, ApprovalStatus.APPROVED, now);
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        // pick the latest by expireAt
        ToolApprovalEntity latest = entities.stream()
                .max((a, b) -> a.getExpireAt().compareTo(b.getExpireAt()))
                .orElseThrow();
        return Optional.of(toRecord(latest));
    }

    @Override
    public Optional<ApprovalRecord> findRecentApproved(
            String tenantId, String toolId, String paramsHash, Duration lookback) {
        if (tenantId == null || toolId == null || lookback == null) {
            return Optional.empty();
        }
        Instant cutoff = Instant.now().minus(lookback);
        Optional<ToolApprovalEntity> opt;
        if (paramsHash != null) {
            opt = repository.findFirstByTenantIdAndToolIdAndParamsHashAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                    tenantId, toolId, paramsHash, ApprovalStatus.APPROVED, cutoff);
        } else {
            opt = repository.findFirstByTenantIdAndToolIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                    tenantId, toolId, ApprovalStatus.APPROVED, cutoff);
        }
        return opt.map(this::toRecord);
    }

    // ==================== Approval lifecycle (T5) ====================

    @Override
    @Transactional
    public String submit(ApprovalRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("approval request 不能为空");
        }
        if (request.getToolId() == null || request.getToolId().isBlank()) {
            throw new IllegalArgumentException("approval request 缺少 toolId");
        }
        if (request.getApplicant() == null || request.getApplicant().isBlank()) {
            throw new IllegalArgumentException("approval request 缺少 applicant");
        }

        String approvalId = generateApprovalId();
        Instant now = Instant.now();
        Instant expireAt = resolveExpireAt(request, now);

        ToolApprovalEntity entity = new ToolApprovalEntity(
                approvalId,
                request.getToolId(),
                nullTo(request.getTaskId(), "task-" + fallbackIdSeq.incrementAndGet()),
                request.getAgentId() != null ? request.getAgentId() : 0L,
                nullTo(request.getInputSnapshot(), "{}"),
                request.getApplicant(),
                expireAt);
        entity.setTenantId(request.getTenantId());
        entity.setParamsHash(request.getParamsHash());
        entity.setReason(request.getReason());
        entity.setStatus(ApprovalStatus.PENDING);
        repository.save(entity);
        log.info("提交审批: approvalId={}, toolId={}, tenant={}, applicant={}, expireAt={}",
                approvalId, request.getToolId(), request.getTenantId(),
                request.getApplicant(), expireAt);
        return approvalId;
    }

    @Override
    public ApprovalDecision await(String approvalId, Duration timeout) {
        if (approvalId == null || approvalId.isBlank()) {
            return ApprovalDecision.TIMEOUT;
        }
        Duration sla = timeout != null ? timeout : DEFAULT_SLA;
        if (sla.compareTo(MIN_SLA) < 0) {
            sla = MIN_SLA;
        }
        ApprovalDecision decision = waiter.await(approvalId, sla);
        // On timeout, mark the entity EXPIRED so cleanup is idempotent
        if (decision == ApprovalDecision.TIMEOUT) {
            markExpired(approvalId);
        }
        return decision;
    }

    @Override
    @Transactional
    public void approve(String approvalId, String approver, String comment) {
        decide(approvalId, approver, comment, ApprovalStatus.APPROVED, ApprovalDecision.APPROVED);
    }

    @Override
    @Transactional
    public void reject(String approvalId, String approver, String comment) {
        decide(approvalId, approver, comment, ApprovalStatus.REJECTED, ApprovalDecision.REJECTED);
    }

    private void decide(String approvalId, String approver, String comment,
                        ApprovalStatus newStatus, ApprovalDecision decision) {
        ToolApprovalEntity entity = requirePending(approvalId);
        entity.setStatus(newStatus);
        entity.setApprover(approver);
        entity.setComment(comment);
        repository.save(entity);
        waiter.notify(approvalId, decision);
        log.info("审批决策: approvalId={}, status={}, approver={}, comment={}",
                approvalId, newStatus, approver, comment);
    }

    @Override
    public List<ApprovalRecord> findPendingByApprover(String approver) {
        if (approver == null || approver.isBlank()) {
            return List.of();
        }
        // Current schema has no approver-assignment column for PENDING records,
        // so we treat the applicant as the prospective approver (simplified).
        // Production may add an approver_queue table or a dedicated column.
        List<ToolApprovalEntity> entities = repository
                .findByApplicantAndStatus(approver, ApprovalStatus.PENDING);
        List<ApprovalRecord> records = new ArrayList<>(entities.size());
        for (ToolApprovalEntity e : entities) {
            records.add(toRecord(e));
        }
        return records;
    }

    @Override
    @Transactional
    public int cleanupExpired() {
        Instant now = Instant.now();
        List<ToolApprovalEntity> stale = repository.findByStatusInAndExpireAtBefore(
                List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED), now);
        if (stale.isEmpty()) {
            return 0;
        }
        List<Long> ids = stale.stream().map(ToolApprovalEntity::getId).toList();
        int updated = repository.updateStatusByIdIn(ApprovalStatus.EXPIRED, ids);
        // Notify any waiters still blocked on these approvalIds
        for (ToolApprovalEntity e : stale) {
            waiter.notify(e.getApprovalId(), ApprovalDecision.TIMEOUT);
        }
        log.info("清理过期审批记录: count={}, ids={}", updated, ids);
        return updated;
    }

    // ==================== Backward-compat: direct save (test/ops helper) ====================

    /**
     * Upsert an {@link ApprovalRecord} directly. Used by tests / ops to seed
     * approval state without going through submit+approve lifecycle.
     */
    @Transactional
    public void save(ApprovalRecord record) {
        if (record == null || record.getToolId() == null) {
            log.warn("保存审批记录收到 null 或缺 toolId, 跳过");
            return;
        }
        String approvalId = nullTo(record.getApprovalId(),
                "apr-" + fallbackIdSeq.incrementAndGet());
        record.setApprovalId(approvalId);

        Optional<ToolApprovalEntity> existing = repository.findByApprovalId(approvalId);
        ToolApprovalEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            applyRecordToEntity(record, entity);
        } else {
            entity = toEntity(record, approvalId);
        }
        repository.save(entity);
    }

    /** Current stored record count (test/monitoring helper). */
    public long size() {
        return repository.count();
    }

    // ==================== internals ====================

    private ToolApprovalEntity requirePending(String approvalId) {
        Optional<ToolApprovalEntity> opt = repository.findByApprovalId(approvalId);
        if (opt.isEmpty()) {
            throw new ToolApprovalException(
                    ToolApprovalException.CODE_APPROVAL_NOT_FOUND,
                    "审批单不存在: " + approvalId);
        }
        ToolApprovalEntity entity = opt.get();
        if (entity.getStatus() != ApprovalStatus.PENDING) {
            throw new ToolApprovalException(
                    ToolApprovalException.CODE_APPROVAL_ALREADY_DECIDED,
                    "审批单已决策: " + approvalId + " (当前状态=" + entity.getStatus() + ")");
        }
        return entity;
    }

    private void markExpired(String approvalId) {
        try {
            Optional<ToolApprovalEntity> opt = repository.findByApprovalId(approvalId);
            if (opt.isPresent() && opt.get().getStatus() == ApprovalStatus.PENDING) {
                ToolApprovalEntity entity = opt.get();
                entity.setStatus(ApprovalStatus.EXPIRED);
                repository.save(entity);
            }
        } catch (Exception e) {
            log.warn("标记超时 EXPIRED 失败 (可被 cleanupExpired 兜底): approvalId={}, err={}",
                    approvalId, e.getMessage());
        }
    }

    private Instant resolveExpireAt(ApprovalRequest request, Instant now) {
        if (request.getExpireAt() != null) {
            return request.getExpireAt();
        }
        Duration sla = request.getSla() != null ? request.getSla() : DEFAULT_SLA;
        if (sla.compareTo(MIN_SLA) < 0) {
            sla = MIN_SLA;
        }
        return now.plus(sla);
    }

    private String generateApprovalId() {
        return "apr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String nullTo(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    // ==================== mappers ====================

    private ApprovalRecord toRecord(ToolApprovalEntity entity) {
        ApprovalRecord record = new ApprovalRecord();
        record.setApprovalId(entity.getApprovalId());
        record.setToolId(entity.getToolId());
        record.setStatus(mapStatusToString(entity.getStatus()));
        record.setPrimaryApprover(entity.getApprover());
        record.setExpireAt(entity.getExpireAt());
        if (entity.getUpdatedAt() != null) {
            record.setApprovedAt(entity.getUpdatedAt());
        }
        if (entity.getExpireAt() != null && entity.getUpdatedAt() != null) {
            long windowSeconds = Duration.between(entity.getUpdatedAt(), entity.getExpireAt()).getSeconds();
            if (windowSeconds > 0) {
                record.setValidityWindowSeconds(windowSeconds);
            }
        }
        return record;
    }

    private ToolApprovalEntity toEntity(ApprovalRecord record, String approvalId) {
        Instant now = Instant.now();
        Instant expireAt = record.getExpireAt() != null ? record.getExpireAt() : now.plus(DEFAULT_SLA);
        ToolApprovalEntity entity = new ToolApprovalEntity(
                approvalId,
                record.getToolId(),
                "task-direct-save",
                0L,
                "{}",
                nullTo(record.getPrimaryApprover(), "system"),
                expireAt);
        entity.setStatus(mapStringToStatus(record.getStatus()));
        entity.setApprover(record.getPrimaryApprover());
        return entity;
    }

    private void applyRecordToEntity(ApprovalRecord record, ToolApprovalEntity entity) {
        entity.setStatus(mapStringToStatus(record.getStatus()));
        if (record.getPrimaryApprover() != null) {
            entity.setApprover(record.getPrimaryApprover());
        }
        if (record.getExpireAt() != null) {
            entity.setExpireAt(record.getExpireAt());
        }
    }

    private String mapStatusToString(ApprovalStatus status) {
        if (status == null) return ApprovalRecord.STATUS_PENDING;
        return switch (status) {
            case PENDING -> ApprovalRecord.STATUS_PENDING;
            case APPROVED -> ApprovalRecord.STATUS_APPROVED;
            case REJECTED -> "REJECTED";
            case EXPIRED -> ApprovalRecord.STATUS_EXPIRED;
        };
    }

    private ApprovalStatus mapStringToStatus(String status) {
        if (status == null) return ApprovalStatus.PENDING;
        return switch (status) {
            case ApprovalRecord.STATUS_PENDING, ApprovalRecord.STATUS_PARTIALLY_APPROVED
                    -> ApprovalStatus.PENDING;
            case ApprovalRecord.STATUS_APPROVED -> ApprovalStatus.APPROVED;
            case "REJECTED" -> ApprovalStatus.REJECTED;
            case ApprovalRecord.STATUS_EXPIRED -> ApprovalStatus.EXPIRED;
            default -> ApprovalStatus.PENDING;
        };
    }
}
