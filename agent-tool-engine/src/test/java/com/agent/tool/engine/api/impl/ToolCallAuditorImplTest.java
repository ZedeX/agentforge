package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.audit.AuditLogMapper;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.model.ApprovalRecord;
import com.agent.tool.engine.model.RiskAssessment;
import com.agent.tool.engine.model.ToolCallAuditLog;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.repository.ToolCallLogRepository;
import com.agent.tool.engine.sandbox.SandboxInstance;
import com.agent.tool.engine.sandbox.SandboxSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link ToolCallAuditorImpl} T9 audit persistence tests.
 *
 * <p>Uses {@code @DataJpaTest} + H2 in-memory to verify the full 16-field
 * audit log persistence path and the four query methods. The "REQUIRES_NEW
 * transaction does not roll back with caller" case is exercised via
 * {@link TransactionTemplate}.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({ToolCallAuditorImpl.class, AuditLogMapper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ToolCallAuditorImplTest {

    @Autowired
    private ToolCallAuditorImpl auditor;

    @Autowired
    private ToolCallLogRepository repository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    /**
     * Clean up database before each test method.
     *
     * <p>record() uses {@code @Transactional(REQUIRES_NEW)} which commits to
     * an independent transaction, so @DataJpaTest's default rollback does NOT
     * undo those writes. Without cleanup, countByStatus and other queries
     * would see records accumulated from previous test methods.</p>
     *
     * <p>Uses a {@code REQUIRES_NEW} {@link TransactionTemplate} so the bulk
     * delete commits in its own transaction, independent of any test-level
     * transaction. Followed by {@code entityManager.clear()} to bypass the
     * first-level cache which would otherwise retain stale entities from
     * prior test methods.</p>
     */
    @BeforeEach
    void cleanDatabase() {
        TransactionTemplate cleanupTx = new TransactionTemplate(txManager);
        cleanupTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        cleanupTx.execute(status -> {
            repository.deleteAllInBatch();
            return null;
        });
        // Clear first-level cache so subsequent queries hit the database.
        entityManager.clear();
    }

    // ==================== Helpers ====================

    private ToolCallRequest buildRequest(String toolId, String tenantId, String traceId) {
        ToolCallRequest req = new ToolCallRequest(toolId, "{\"q\":\"test\"}");
        req.setTenantId(tenantId);
        req.setTraceId(traceId);
        req.setAgentId(1001L);
        req.setTaskId("task-001");
        req.setInputHash("a".repeat(64));
        return req;
    }

    private RiskAssessment r1Assessment() {
        return new RiskAssessment(ToolRiskLevel.R1, false, "low-risk read-only");
    }

    private RiskAssessment r3Assessment() {
        return new RiskAssessment(ToolRiskLevel.R3, true, "high-risk sandbox");
    }

    private ToolCallResult successResult() {
        ToolCallResult result = new ToolCallResult("tool_ok", "hello world", ToolCallStatus.SUCCESS);
        result.setOutputTokens(42);
        return result;
    }

    private ToolCallResult failedResult(String error) {
        ToolCallResult result = new ToolCallResult("tool_fail", "", ToolCallStatus.FAILED);
        result.setErrorStack(error);
        return result;
    }

    private ToolCallResult timeoutResult() {
        ToolCallResult result = new ToolCallResult("tool_timeout", "", ToolCallStatus.TIMEOUT);
        result.setErrorStack("exec timeout after 5000ms");
        return result;
    }

    private ApprovalRecord approvedRecord() {
        ApprovalRecord rec = new ApprovalRecord();
        rec.setApprovalId("appr-001");
        rec.setPrimaryApprover("alice");
        rec.setStatus(ApprovalRecord.STATUS_APPROVED);
        rec.setApprovedAt(Instant.now());
        return rec;
    }

    private SandboxInstance sandboxInstance() {
        return new SandboxInstance("ctr-abc123", SandboxSpec.builder().build());
    }

    // ==================== T9 Plan §Red tests ====================

    @Test
    @DisplayName("record_success_persistsAllFields: SUCCESS → 16 fields persisted")
    void record_success_persistsAllFields() {
        ToolCallRequest req = buildRequest("tool_ok", "tenant-A", "trace-success");
        ToolCallResult result = successResult();

        String logId = auditor.record(req, r1Assessment(), null, result, null);

        assertThat(logId).isNotNull();
        Optional<ToolCallAuditLog> fetched = auditor.findByCallId("trace-success");
        assertThat(fetched).isPresent();
        ToolCallAuditLog log = fetched.get();
        // T9 16-field assertions
        assertThat(log.getCallId()).isEqualTo("trace-success");
        assertThat(log.getTenantId()).isEqualTo("tenant-A");
        assertThat(log.getAgentId()).isEqualTo(1001L);
        assertThat(log.getToolId()).isEqualTo("tool_ok");
        assertThat(log.getParamsHash()).isEqualTo("a".repeat(64));
        assertThat(log.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(log.getRiskLevel()).isEqualTo(ToolRiskLevel.R1);
        assertThat(log.getStartedAt()).isNotNull();
        assertThat(log.getEndedAt()).isNotNull();
        assertThat(log.getDurationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(log.getCostTokens()).isEqualTo(42);
        assertThat(log.getErrorMessage()).isNull();
        assertThat(log.getSandboxContainerId()).isNull();
        assertThat(log.getApproverId()).isNull();
        assertThat(log.isCacheHit()).isFalse();
        assertThat(log.getToolVersion()).isEqualTo(1);
        // Legacy fields
        assertThat(log.getTraceId()).isEqualTo("trace-success");
        assertThat(log.getInputJson()).isEqualTo("{\"q\":\"test\"}");
        assertThat(log.getOutput()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("record_failed_persistsErrorMessage: FAILED → errorMessage persisted")
    void record_failed_persistsErrorMessage() {
        ToolCallRequest req = buildRequest("tool_fail", "tenant-A", "trace-fail");
        ToolCallResult result = failedResult("NullPointerException@line42");

        String logId = auditor.record(req, r3Assessment(), sandboxInstance(), result, approvedRecord());

        assertThat(logId).isNotNull();
        ToolCallAuditLog log = auditor.findByCallId("trace-fail").orElseThrow();
        assertThat(log.getStatus()).isEqualTo(ToolCallStatus.FAILED);
        assertThat(log.getErrorMessage()).contains("NullPointerException");
        assertThat(log.getSandboxContainerId()).isEqualTo("ctr-abc123");
        assertThat(log.getApproverId()).isEqualTo("alice");
        assertThat(log.getRiskLevel()).isEqualTo(ToolRiskLevel.R3);
    }

    @Test
    @DisplayName("record_timeout_persistsStatus: TIMEOUT → status persisted")
    void record_timeout_persistsStatus() {
        ToolCallRequest req = buildRequest("tool_to", "tenant-A", "trace-timeout");
        ToolCallResult result = timeoutResult();

        String logId = auditor.record(req, r1Assessment(), null, result, null);

        assertThat(logId).isNotNull();
        ToolCallAuditLog log = auditor.findByCallId("trace-timeout").orElseThrow();
        assertThat(log.getStatus()).isEqualTo(ToolCallStatus.TIMEOUT);
        assertThat(log.getErrorMessage()).contains("exec timeout");
    }

    @Test
    @DisplayName("record_isTransactional_failureRollsBack: audit commits in REQUIRES_NEW even if caller rolls back")
    void record_isTransactional_failureRollsBack() {
        // Simulate caller transaction that rolls back after audit record is written.
        // The audit entry should still be visible (REQUIRES_NEW → independent commit).
        TransactionTemplate callerTx = new TransactionTemplate(txManager);
        callerTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        ToolCallRequest req = buildRequest("tool_iso", "tenant-iso", "trace-iso");
        ToolCallResult result = successResult();

        // Run audit inside an outer transaction that will be marked rollback-only.
        TransactionTemplate outerTx = new TransactionTemplate(txManager);
        outerTx.execute(status -> {
            auditor.record(req, r1Assessment(), null, result, null);
            // Simulate business failure: mark outer transaction for rollback.
            status.setRollbackOnly();
            return null;
        });

        // Even though outer transaction rolled back, audit entry must be visible.
        Optional<ToolCallAuditLog> fetched = auditor.findByCallId("trace-iso");
        assertThat(fetched).isPresent();
        assertThat(fetched.get().getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
    }

    @Test
    @DisplayName("record_async_returnsImmediately: record() returns quickly (sync but fast)")
    void record_async_returnsImmediately() {
        ToolCallRequest req = buildRequest("tool_fast", "tenant-A", "trace-fast");
        ToolCallResult result = successResult();

        long start = System.nanoTime();
        String logId = auditor.record(req, r1Assessment(), null, result, null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(logId).isNotNull();
        // record() is synchronous but should complete in well under 2 seconds on H2.
        assertThat(elapsedMs).isLessThan(2000L);
    }

    @Test
    @DisplayName("findByCallId_returnsRecord: query by callId")
    void findByCallId_returnsRecord() {
        ToolCallRequest req = buildRequest("tool_q", "tenant-A", "trace-findByCallId");
        auditor.record(req, r1Assessment(), null, successResult(), null);

        Optional<ToolCallAuditLog> fetched = auditor.findByCallId("trace-findByCallId");
        assertThat(fetched).isPresent();
        assertThat(fetched.get().getToolId()).isEqualTo("tool_q");

        // Unknown callId returns empty.
        assertThat(auditor.findByCallId("nonexistent")).isEmpty();
        assertThat(auditor.findByCallId(null)).isEmpty();
        assertThat(auditor.findByCallId("")).isEmpty();
    }

    @Test
    @DisplayName("findByTenantIdAndTimeRange: paginated tenant + time range query")
    void findByTenantIdAndTimeRange() {
        // Use a wide window (1 hour) to avoid H2 timestamp-with-timezone precision issues.
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        // Write 3 records for tenant-A, 1 for tenant-B.
        auditor.record(buildRequest("t1", "tenant-A", "c1"), r1Assessment(), null, successResult(), null);
        auditor.record(buildRequest("t2", "tenant-A", "c2"), r1Assessment(), null, successResult(), null);
        auditor.record(buildRequest("t3", "tenant-A", "c3"), r1Assessment(), null, successResult(), null);
        auditor.record(buildRequest("t4", "tenant-B", "c4"), r1Assessment(), null, successResult(), null);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        Pageable pageable = PageRequest.of(0, 10);
        Page<ToolCallAuditLog> page = auditor.findByTenantIdAndTimeRange("tenant-A", from, to, pageable);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).allSatisfy(log ->
                assertThat(log.getTenantId()).isEqualTo("tenant-A"));

        // tenant-B should have only 1 record.
        Page<ToolCallAuditLog> pageB = auditor.findByTenantIdAndTimeRange("tenant-B", from, to, pageable);
        assertThat(pageB.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByTenantIdAndToolId: paginated tenant + toolId query")
    void findByTenantIdAndToolId() {
        auditor.record(buildRequest("toolX", "tenant-A", "c-x1"), r1Assessment(), null, successResult(), null);
        auditor.record(buildRequest("toolX", "tenant-A", "c-x2"), r1Assessment(), null, successResult(), null);
        auditor.record(buildRequest("toolY", "tenant-A", "c-y1"), r1Assessment(), null, successResult(), null);
        auditor.record(buildRequest("toolX", "tenant-B", "c-x3"), r1Assessment(), null, successResult(), null);

        Pageable pageable = PageRequest.of(0, 10);
        Page<ToolCallAuditLog> page = auditor.findByTenantIdAndToolId("tenant-A", "toolX", pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allSatisfy(log -> {
            assertThat(log.getTenantId()).isEqualTo("tenant-A");
            assertThat(log.getToolId()).isEqualTo("toolX");
        });
    }

    @Test
    @DisplayName("countByStatus: count by tenant + status")
    void countByStatus() {
        auditor.record(buildRequest("tS1", "tenant-A", "cs1"), r1Assessment(), null, successResult(), null);
        auditor.record(buildRequest("tS2", "tenant-A", "cs2"), r1Assessment(), null, successResult(), null);
        auditor.record(buildRequest("tF1", "tenant-A", "cf1"), r1Assessment(), null, failedResult("err"), null);
        auditor.record(buildRequest("tF2", "tenant-A", "cf2"), r1Assessment(), null, failedResult("err"), null);
        auditor.record(buildRequest("tF3", "tenant-A", "cf3"), r1Assessment(), null, failedResult("err"), null);
        auditor.record(buildRequest("tS3", "tenant-B", "cs3"), r1Assessment(), null, successResult(), null);

        long successCountA = auditor.countByStatus("tenant-A", ToolCallStatus.SUCCESS);
        long failedCountA = auditor.countByStatus("tenant-A", ToolCallStatus.FAILED);
        long successCountB = auditor.countByStatus("tenant-B", ToolCallStatus.SUCCESS);

        assertThat(successCountA).isEqualTo(2L);
        assertThat(failedCountA).isEqualTo(3L);
        assertThat(successCountB).isEqualTo(1L);
        // Empty tenant returns 0.
        assertThat(auditor.countByStatus("tenant-empty", ToolCallStatus.SUCCESS)).isZero();
    }

    // ==================== Legacy audit() backward compatibility ====================

    @Test
    @DisplayName("audit_legacy_api_stillWorks: T8 ToolGatewayImpl path persists via JPA")
    void audit_legacy_api_stillWorks() {
        ToolCallAuditLog legacy = new ToolCallAuditLog("trace-leg", "tool_leg", ToolCallStatus.SUCCESS);
        legacy.setTenantId("tenant-legacy");
        legacy.setInputJson("{\"q\":\"legacy\"}");
        legacy.setOutput("legacy-out");

        auditor.audit(legacy);

        assertThat(legacy.getLogId()).isNotNull();
        // Legacy audit() persists via JPA (callId defaults to traceId).
        Optional<ToolCallAuditLog> fetched = auditor.findByCallId("trace-leg");
        assertThat(fetched).isPresent();
        assertThat(fetched.get().getOutput()).isEqualTo("legacy-out");
    }

    @Test
    @DisplayName("audit_null_log_skipped: null entry does not throw")
    void audit_null_log_skipped() {
        auditor.audit(null);
        // No exception thrown; repository stays empty.
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("record_withNullApprovalOrSandbox_doesNotThrow")
    void record_withNullApprovalOrSandbox_doesNotThrow() {
        ToolCallRequest req = buildRequest("tool_null_ctx", "tenant-A", "trace-null-ctx");
        ToolCallResult result = successResult();

        // Both sandbox and approval are null (R1 path). Should not throw.
        String logId = auditor.record(req, r1Assessment(), null, result, null);

        assertThat(logId).isNotNull();
        ToolCallAuditLog log = auditor.findByCallId("trace-null-ctx").orElseThrow();
        assertThat(log.getSandboxContainerId()).isNull();
        assertThat(log.getApproverId()).isNull();
    }

    @Test
    @DisplayName("record_cacheHitFlag_persisted_whenResultFromCache")
    void record_cacheHitFlag_persisted_whenResultFromCache() {
        ToolCallRequest req = buildRequest("tool_cache", "tenant-A", "trace-cache");
        ToolCallResult result = new ToolCallResult("tool_cache", "cached-out", ToolCallStatus.CACHED);
        result.setFromCache(true);

        String logId = auditor.record(req, r1Assessment(), null, result, null);

        assertThat(logId).isNotNull();
        ToolCallAuditLog log = auditor.findByCallId("trace-cache").orElseThrow();
        assertThat(log.isCacheHit()).isTrue();
        assertThat(log.getStatus()).isEqualTo(ToolCallStatus.CACHED);
        // occurredAt should be set close to now (within 5 seconds tolerance).
        assertThat(log.getOccurredAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }
}
