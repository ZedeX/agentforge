package com.agent.tool.engine.entity;

import com.agent.tool.engine.enums.ApprovalStatus;
import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.QuotaSubjectType;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.enums.ToolStatus;
import com.agent.tool.engine.enums.ToolType;
import com.agent.tool.engine.repository.ToolApprovalRepository;
import com.agent.tool.engine.repository.ToolCallLogRepository;
import com.agent.tool.engine.repository.ToolQuotaRepository;
import com.agent.tool.engine.repository.ToolRegistryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * T2 JPA Entity + Repository 持久化测试.
 *
 * <p>验证 4 个 Entity (ToolRegistryEntity / ToolCallLogEntity / ToolQuotaEntity /
 * ToolApprovalEntity) 能正确映射到 H2 表并可通过 Repository 增删改查.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
class ToolEntityRepositoryTest {

    @Autowired
    private ToolRegistryRepository registryRepository;

    @Autowired
    private ToolCallLogRepository callLogRepository;

    @Autowired
    private ToolQuotaRepository quotaRepository;

    @Autowired
    private ToolApprovalRepository approvalRepository;

    // ==================== ToolRegistryEntity ====================

    @Test
    @DisplayName("ToolRegistryEntity: 持久化 + 按 toolId 查询")
    void should_PersistAndFind_When_ToolRegistrySaved() {
        ToolRegistryEntity entity = new ToolRegistryEntity(
                "tool_t2_1", "t2-search", "T2 搜索工具", "测试搜索工具",
                ToolType.ATOMIC, ToolRiskLevel.R1.getLevel(), ExecutorType.HTTP_API,
                "grpc://tool-service/Search", 3000);
        entity.setSceneTags("[\"search\",\"qa\"]");
        entity.setInputSchema("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}}");
        entity.setOutputSchema("{\"type\":\"object\",\"properties\":{\"hits\":{\"type\":\"array\"}}}");
        entity.setErrorCodes("[]");

        ToolRegistryEntity saved = registryRepository.save(entity);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersionLock()).isZero();

        Optional<ToolRegistryEntity> found = registryRepository.findByToolId("tool_t2_1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("t2-search");
        assertThat(found.get().getExecutorType()).isEqualTo(ExecutorType.HTTP_API);
        assertThat(found.get().getRiskLevelEnum()).isEqualTo(ToolRiskLevel.R1);
        assertThat(found.get().getStatusEnum()).isEqualTo(ToolStatus.ENABLED);
    }

    @Test
    @DisplayName("ToolRegistryEntity: 重复 toolId 触发唯一约束异常")
    void should_Throw_When_DuplicateToolId() {
        ToolRegistryEntity e1 = buildRegistryEntity("tool_dup", "dup-name");
        registryRepository.saveAndFlush(e1);

        ToolRegistryEntity e2 = buildRegistryEntity("tool_dup", "another-name");
        try {
            registryRepository.saveAndFlush(e2);
            assertThat(false).as("Expected DataIntegrityViolationException").isTrue();
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            assertThat(ex).isNotNull();
        }
    }

    @Test
    @DisplayName("ToolRegistryEntity: 按状态 + 风险等级查询")
    void should_FindByStatusAndRiskLevel() {
        registryRepository.save(buildRegistryEntity("tool_r1_a", "r1a",
                ToolRiskLevel.R1.getLevel()));
        registryRepository.save(buildRegistryEntity("tool_r1_b", "r1b",
                ToolRiskLevel.R1.getLevel()));
        registryRepository.save(buildRegistryEntity("tool_r3_a", "r3a",
                ToolRiskLevel.R3.getLevel()));
        registryRepository.flush();

        List<ToolRegistryEntity> r1Tools = registryRepository.findByStatusAndRiskLevel(
                ToolStatus.ENABLED.getCode(), ToolRiskLevel.R1.getLevel());

        assertThat(r1Tools).hasSize(2);
        assertThat(r1Tools).allSatisfy(t -> assertThat(t.getRiskLevel()).isEqualTo(1));
    }

    // ==================== ToolCallLogEntity ====================

    @Test
    @DisplayName("ToolCallLogEntity: 持久化 + 按 callId 查询")
    void should_PersistAndFind_When_CallLogSaved() {
        ToolCallLogEntity log = new ToolCallLogEntity(
                "call_t2_1", "task_t2_1", 1001L, "tool_t2_1", 1,
                "{\"q\":\"test\"}", "success", 150, 1, "trace_t2_1");
        callLogRepository.save(log);

        Optional<ToolCallLogEntity> found = callLogRepository.findByCallId("call_t2_1");
        assertThat(found).isPresent();
        assertThat(found.get().getTaskId()).isEqualTo("task_t2_1");
        assertThat(found.get().getStatus()).isEqualTo("success");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ToolCallLogEntity: 按 taskId 查询并按 stepNo 排序")
    void should_FindByTaskIdOrderedByStepNo() {
        callLogRepository.save(buildCallLog("c1", "task_sort", 1));
        callLogRepository.save(buildCallLog("c3", "task_sort", 3));
        callLogRepository.save(buildCallLog("c2", "task_sort", 2));
        callLogRepository.flush();

        List<ToolCallLogEntity> logs = callLogRepository.findByTaskIdOrderByStepNoAsc("task_sort");
        assertThat(logs).hasSize(3);
        assertThat(logs.get(0).getStepNo()).isEqualTo(1);
        assertThat(logs.get(1).getStepNo()).isEqualTo(2);
        assertThat(logs.get(2).getStepNo()).isEqualTo(3);
    }

    // ==================== ToolQuotaEntity ====================

    @Test
    @DisplayName("ToolQuotaEntity: 持久化 + 按主体 + 工具查询")
    void should_PersistAndFind_When_QuotaSaved() {
        ToolQuotaEntity quota = new ToolQuotaEntity(
                QuotaSubjectType.TENANT, "tn_t2", "tool_t2_1",
                100, 10000L, Instant.now().plus(1, ChronoUnit.HOURS));
        quotaRepository.save(quota);

        Optional<ToolQuotaEntity> found = quotaRepository.findBySubjectTypeAndSubjectIdAndToolId(
                QuotaSubjectType.TENANT, "tn_t2", "tool_t2_1");
        assertThat(found).isPresent();
        assertThat(found.get().getDailyLimit()).isEqualTo(100);
        assertThat(found.get().getDailyUsed()).isZero();
    }

    @Test
    @DisplayName("ToolQuotaEntity: 原子扣减配额 (乐观锁)")
    void should_TryConsumeQuota_When_QuotaAvailable() {
        ToolQuotaEntity quota = new ToolQuotaEntity(
                QuotaSubjectType.TENANT, "tn_consume", "tool_consume",
                5, 1000L, Instant.now().plus(1, ChronoUnit.HOURS));
        quota = quotaRepository.saveAndFlush(quota);

        // 验证 entity 已持久化
        assertThat(quota.getId()).isNotNull();
        assertThat(quotaRepository.count()).isEqualTo(1);

        int affected = quotaRepository.tryConsumeQuota(quota.getId(), quota.getVersion(), 100L);
        assertThat(affected).as("UPDATE should match 1 row (id=%s, version=%s)",
                quota.getId(), quota.getVersion()).isEqualTo(1);

        ToolQuotaEntity updated = quotaRepository.findById(quota.getId()).orElseThrow();
        assertThat(updated.getDailyUsed()).isEqualTo(1);
        assertThat(updated.getCostUsedCent()).isEqualTo(100L);
    }

    @Test
    @DisplayName("ToolQuotaEntity: 配额耗尽时扣减返回 0")
    void should_ReturnZero_When_QuotaExhausted() {
        ToolQuotaEntity quota = new ToolQuotaEntity(
                QuotaSubjectType.TENANT, "tn_exhaust", "tool_exhaust",
                1, 1000L, Instant.now().plus(1, ChronoUnit.HOURS));
        quota = quotaRepository.saveAndFlush(quota);

        // 第一次扣减成功
        int affected1 = quotaRepository.tryConsumeQuota(quota.getId(), quota.getVersion(), 50L);
        assertThat(affected1).isEqualTo(1);

        // 用旧 version 再次扣减应失败 (version 不匹配)
        int affected2 = quotaRepository.tryConsumeQuota(quota.getId(), 0, 50L);
        assertThat(affected2).isZero();

        // 用新 version 扣减也应失败 (daily_used >= daily_limit)
        ToolQuotaEntity refreshed = quotaRepository.findById(quota.getId()).orElseThrow();
        int affected3 = quotaRepository.tryConsumeQuota(quota.getId(), refreshed.getVersion(), 50L);
        assertThat(affected3).isZero();
    }

    // ==================== ToolApprovalEntity ====================

    @Test
    @DisplayName("ToolApprovalEntity: 持久化 + 按 approvalId 查询")
    void should_PersistAndFind_When_ApprovalSaved() {
        ToolApprovalEntity approval = new ToolApprovalEntity(
                "apr_t2_1", "tool_r3", "task_t2", 2001L,
                "{\"x\":1}", "applicant_1", Instant.now().plus(1, ChronoUnit.HOURS));
        approvalRepository.save(approval);

        Optional<ToolApprovalEntity> found = approvalRepository.findByApprovalId("apr_t2_1");
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(found.get().getApplicant()).isEqualTo("applicant_1");
    }

    @Test
    @DisplayName("ToolApprovalEntity: 查询已批准且未过期的审批")
    void should_FindApprovedAndNotExpired() {
        // 已批准 + 未过期
        ToolApprovalEntity active = new ToolApprovalEntity(
                "apr_active", "tool_r3_a", "task_a", 2001L,
                "{}", "u1", Instant.now().plus(2, ChronoUnit.HOURS));
        active.setStatus(ApprovalStatus.APPROVED);
        active.setApprover("approver_1");
        approvalRepository.save(active);

        // 已批准 + 已过期
        ToolApprovalEntity expired = new ToolApprovalEntity(
                "apr_expired", "tool_r3_a", "task_b", 2002L,
                "{}", "u2", Instant.now().minus(1, ChronoUnit.HOURS));
        expired.setStatus(ApprovalStatus.APPROVED);
        expired.setApprover("approver_2");
        approvalRepository.save(expired);

        approvalRepository.flush();

        List<ToolApprovalEntity> valid = approvalRepository.findByToolIdAndStatusAndExpireAtAfter(
                "tool_r3_a", ApprovalStatus.APPROVED, Instant.now());

        assertThat(valid).hasSize(1);
        assertThat(valid.get(0).getApprovalId()).isEqualTo("apr_active");
    }

    // ==================== 辅助方法 ====================

    private ToolRegistryEntity buildRegistryEntity(String toolId, String name) {
        return buildRegistryEntity(toolId, name, ToolRiskLevel.R1.getLevel());
    }

    private ToolRegistryEntity buildRegistryEntity(String toolId, String name, int riskLevel) {
        ToolRegistryEntity entity = new ToolRegistryEntity(
                toolId, name, name + " display", "desc",
                ToolType.ATOMIC, riskLevel, ExecutorType.HTTP_API,
                "grpc://tool/" + name, 5000);
        entity.setSceneTags("[\"test\"]");
        entity.setInputSchema("{}");
        entity.setOutputSchema("{}");
        entity.setErrorCodes("[]");
        return entity;
    }

    private ToolCallLogEntity buildCallLog(String callId, String taskId, int stepNo) {
        ToolCallLogEntity log = new ToolCallLogEntity(
                callId, taskId, 1000L, "tool_t2", 1,
                "{}", "success", 100, 1, "trace_" + callId);
        log.setStepNo(stepNo);
        return log;
    }
}
