package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.approval.ApprovalWaiter;
import com.agent.tool.engine.entity.ToolApprovalEntity;
import com.agent.tool.engine.enums.ApprovalDecision;
import com.agent.tool.engine.enums.ApprovalStatus;
import com.agent.tool.engine.exception.ToolApprovalException;
import com.agent.tool.engine.model.ApprovalRecord;
import com.agent.tool.engine.model.ApprovalRequest;
import com.agent.tool.engine.repository.ToolApprovalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalStoreImpl} 单元测试 (T5 doc 05-tool-engine §4.3 / §4.4).
 *
 * <p>使用 mock {@link ToolApprovalRepository} + 真实 {@link ApprovalWaiter}
 * 组合测试, 覆盖完整审批生命周期:
 * <ul>
 *   <li>查询: findValid / findRecentApproved / findPendingByApprover</li>
 *   <li>提交: submit (校验 + 持久化 PENDING)</li>
 *   <li>等待: await (通知到达 / SLA 超时 / null 短路)</li>
 *   <li>决策: approve / reject (含 NOT_FOUND / ALREADY_DECIDED 异常)</li>
 *   <li>清理: cleanupExpired (标记 EXPIRED + 通知等待者)</li>
 *   <li>向后兼容: save (test/ops 直写, insert + update)</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalStoreImplTest {

    @Mock
    private ToolApprovalRepository repository;

    private ApprovalWaiter waiter;
    private ApprovalStoreImpl store;

    @BeforeEach
    void setUp() {
        waiter = new ApprovalWaiter();
        store = new ApprovalStoreImpl(repository, waiter);
    }

    // ==================== findValid ====================

    @Test
    @DisplayName("findValid: 无 APPROVED 记录返回 empty")
    void findValid_returnsEmpty_When_NoApprovedRecord() {
        when(repository.findByToolIdAndStatusAndExpireAtAfter(
                eq("tool_x"), eq(ApprovalStatus.APPROVED), any(Instant.class)))
                .thenReturn(List.of());

        Optional<ApprovalRecord> result = store.findValid("tool_x");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findValid: 有 APPROVED 未过期记录返回最新一条")
    void findValid_returnsRecord_When_ApprovedAndUnexpired() {
        ToolApprovalEntity entity = buildEntity("apr-1", "tool_y", ApprovalStatus.APPROVED,
                Instant.now().plus(Duration.ofHours(1)));
        entity.setApprover("u_p");
        when(repository.findByToolIdAndStatusAndExpireAtAfter(
                eq("tool_y"), eq(ApprovalStatus.APPROVED), any(Instant.class)))
                .thenReturn(List.of(entity));

        Optional<ApprovalRecord> result = store.findValid("tool_y");

        assertThat(result).isPresent();
        assertThat(result.get().getApprovalId()).isEqualTo("apr-1");
        assertThat(result.get().getToolId()).isEqualTo("tool_y");
        assertThat(result.get().getStatus()).isEqualTo(ApprovalRecord.STATUS_APPROVED);
        assertThat(result.get().getPrimaryApprover()).isEqualTo("u_p");
    }

    @Test
    @DisplayName("findValid: toolId 为 null 或空返回 empty (短路)")
    void findValid_returnsEmpty_When_ToolIdNullOrBlank() {
        assertThat(store.findValid(null)).isEmpty();
        assertThat(store.findValid("")).isEmpty();
        assertThat(store.findValid("   ")).isEmpty();
    }

    // ==================== findRecentApproved ====================

    @Test
    @DisplayName("findRecentApproved: paramsHash 匹配返回记录")
    void findRecentApproved_returnsRecord_When_ParamsHashMatches() {
        ToolApprovalEntity entity = buildEntity("apr-2", "tool_r", ApprovalStatus.APPROVED,
                Instant.now().plus(Duration.ofHours(1)));
        when(repository.findFirstByTenantIdAndToolIdAndParamsHashAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                eq("tn_1"), eq("tool_r"), eq("hash_1"), eq(ApprovalStatus.APPROVED),
                any(Instant.class)))
                .thenReturn(Optional.of(entity));

        Optional<ApprovalRecord> result = store.findRecentApproved(
                "tn_1", "tool_r", "hash_1", Duration.ofHours(1));

        assertThat(result).isPresent();
        assertThat(result.get().getApprovalId()).isEqualTo("apr-2");
    }

    @Test
    @DisplayName("findRecentApproved: paramsHash 为 null 走任意 params 查询分支")
    void findRecentApproved_returnsRecord_When_ParamsHashNull() {
        ToolApprovalEntity entity = buildEntity("apr-3", "tool_r", ApprovalStatus.APPROVED,
                Instant.now().plus(Duration.ofHours(1)));
        when(repository.findFirstByTenantIdAndToolIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                eq("tn_1"), eq("tool_r"), eq(ApprovalStatus.APPROVED), any(Instant.class)))
                .thenReturn(Optional.of(entity));

        Optional<ApprovalRecord> result = store.findRecentApproved(
                "tn_1", "tool_r", null, Duration.ofHours(1));

        assertThat(result).isPresent();
        assertThat(result.get().getApprovalId()).isEqualTo("apr-3");
    }

    @Test
    @DisplayName("findRecentApproved: tenantId/toolId/lookback 为 null 返回 empty (短路)")
    void findRecentApproved_returnsEmpty_When_InputsNull() {
        assertThat(store.findRecentApproved(null, "tool", "hash", Duration.ofHours(1))).isEmpty();
        assertThat(store.findRecentApproved("tn", null, "hash", Duration.ofHours(1))).isEmpty();
        assertThat(store.findRecentApproved("tn", "tool", "hash", null)).isEmpty();
    }

    // ==================== findPendingByApprover ====================

    @Test
    @DisplayName("findPendingByApprover: approver 为 null 或空返回空列表 (短路)")
    void findPendingByApprover_returnsEmpty_When_ApproverNullOrBlank() {
        assertThat(store.findPendingByApprover(null)).isEmpty();
        assertThat(store.findPendingByApprover("")).isEmpty();
        assertThat(store.findPendingByApprover("   ")).isEmpty();
    }

    @Test
    @DisplayName("findPendingByApprover: 返回 approver 名下 PENDING 记录列表")
    void findPendingByApprover_returnsList_When_ApproverHasPending() {
        ToolApprovalEntity e1 = buildEntity("apr-a", "tool_1", ApprovalStatus.PENDING,
                Instant.now().plus(Duration.ofHours(1)));
        e1.setApplicant("u_x");
        ToolApprovalEntity e2 = buildEntity("apr-b", "tool_2", ApprovalStatus.PENDING,
                Instant.now().plus(Duration.ofHours(1)));
        e2.setApplicant("u_x");
        when(repository.findByApplicantAndStatus("u_x", ApprovalStatus.PENDING))
                .thenReturn(List.of(e1, e2));

        List<ApprovalRecord> result = store.findPendingByApprover("u_x");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ApprovalRecord::getApprovalId)
                .containsExactlyInAnyOrder("apr-a", "apr-b");
    }

    // ==================== submit ====================

    @Test
    @DisplayName("submit: 合法请求持久化 PENDING 并返回 approvalId")
    void submit_persistsPending_When_RequestValid() {
        ApprovalRequest request = new ApprovalRequest("tool_s", "tn_1", "u_app");
        request.setParamsHash("hash_s");
        request.setReason("测试审批");
        request.setSla(Duration.ofMinutes(15));

        String approvalId = store.submit(request);

        assertThat(approvalId).startsWith("apr-");
        ArgumentCaptor<ToolApprovalEntity> captor = ArgumentCaptor.forClass(ToolApprovalEntity.class);
        verify(repository).save(captor.capture());
        ToolApprovalEntity saved = captor.getValue();
        assertThat(saved.getApprovalId()).isEqualTo(approvalId);
        assertThat(saved.getToolId()).isEqualTo("tool_s");
        assertThat(saved.getTenantId()).isEqualTo("tn_1");
        assertThat(saved.getParamsHash()).isEqualTo("hash_s");
        assertThat(saved.getApplicant()).isEqualTo("u_app");
        assertThat(saved.getReason()).isEqualTo("测试审批");
        assertThat(saved.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(saved.getExpireAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("submit: request 为 null 抛 IllegalArgumentException")
    void submit_throwsIllegalArgument_When_RequestNull() {
        assertThatThrownBy(() -> store.submit(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("submit: 缺少 toolId 抛 IllegalArgumentException")
    void submit_throwsIllegalArgument_When_MissingToolId() {
        ApprovalRequest request = new ApprovalRequest(null, "tn_1", "u_app");

        assertThatThrownBy(() -> store.submit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolId");
    }

    @Test
    @DisplayName("submit: 缺少 applicant 抛 IllegalArgumentException")
    void submit_throwsIllegalArgument_When_MissingApplicant() {
        ApprovalRequest request = new ApprovalRequest("tool_s", "tn_1", null);

        assertThatThrownBy(() -> store.submit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("applicant");
    }

    // ==================== await ====================

    @Test
    @DisplayName("await: 审批人提前通知 APPROVED → 立即返回 APPROVED")
    void await_returnsApproved_When_ApproverNotifiesFirst() {
        waiter.notify("apr-await-ok", ApprovalDecision.APPROVED);

        ApprovalDecision decision = store.await("apr-await-ok", Duration.ofSeconds(1));

        assertThat(decision).isEqualTo(ApprovalDecision.APPROVED);
    }

    @Test
    @DisplayName("await: SLA 超时 → 返回 TIMEOUT 并尝试标记实体 EXPIRED")
    void await_returnsTimeout_When_SlaElapses() {
        ApprovalDecision decision = store.await("apr-await-timeout", Duration.ofMillis(100));

        assertThat(decision).isEqualTo(ApprovalDecision.TIMEOUT);
        // markExpired 调用 findByApprovalId (默认返回 empty, 不做 save)
        verify(repository).findByApprovalId("apr-await-timeout");
    }

    @Test
    @DisplayName("await: approvalId 为 null 或空 → 直接返回 TIMEOUT (短路)")
    void await_returnsTimeout_When_ApprovalIdNullOrBlank() {
        assertThat(store.await(null, Duration.ofSeconds(1))).isEqualTo(ApprovalDecision.TIMEOUT);
        assertThat(store.await("", Duration.ofSeconds(1))).isEqualTo(ApprovalDecision.TIMEOUT);
        assertThat(store.await("   ", Duration.ofSeconds(1))).isEqualTo(ApprovalDecision.TIMEOUT);
    }

    // ==================== approve / reject ====================

    @Test
    @DisplayName("approve: PENDING 记录 → 更新为 APPROVED 并通知等待者")
    void approve_updatesToApproved_When_Pending() {
        ToolApprovalEntity entity = buildEntity("apr-approve", "tool_a", ApprovalStatus.PENDING,
                Instant.now().plus(Duration.ofHours(1)));
        when(repository.findByApprovalId("apr-approve")).thenReturn(Optional.of(entity));

        store.approve("apr-approve", "u_p", "同意");

        ArgumentCaptor<ToolApprovalEntity> captor = ArgumentCaptor.forClass(ToolApprovalEntity.class);
        verify(repository).save(captor.capture());
        ToolApprovalEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(saved.getApprover()).isEqualTo("u_p");
        assertThat(saved.getComment()).isEqualTo("同意");
        assertThat(waiter.peek("apr-approve")).contains(ApprovalDecision.APPROVED);
    }

    @Test
    @DisplayName("approve: 审批单不存在 → 抛 ToolApprovalException (CODE_APPROVAL_NOT_FOUND)")
    void approve_throwsNotFound_When_ApprovalMissing() {
        when(repository.findByApprovalId("apr-ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store.approve("apr-ghost", "u_p", "ok"))
                .isInstanceOf(ToolApprovalException.class)
                .satisfies(ex -> assertThat(((ToolApprovalException) ex).getErrorCode())
                        .isEqualTo(ToolApprovalException.CODE_APPROVAL_NOT_FOUND));
    }

    @Test
    @DisplayName("approve: 审批单已决策 → 抛 ToolApprovalException (CODE_APPROVAL_ALREADY_DECIDED)")
    void approve_throwsAlreadyDecided_When_StatusNotPending() {
        ToolApprovalEntity entity = buildEntity("apr-decided", "tool_a", ApprovalStatus.APPROVED,
                Instant.now().plus(Duration.ofHours(1)));
        when(repository.findByApprovalId("apr-decided")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> store.approve("apr-decided", "u_p", "ok"))
                .isInstanceOf(ToolApprovalException.class)
                .satisfies(ex -> assertThat(((ToolApprovalException) ex).getErrorCode())
                        .isEqualTo(ToolApprovalException.CODE_APPROVAL_ALREADY_DECIDED));
    }

    @Test
    @DisplayName("reject: PENDING 记录 → 更新为 REJECTED 并通知等待者")
    void reject_updatesToRejected_When_Pending() {
        ToolApprovalEntity entity = buildEntity("apr-reject", "tool_a", ApprovalStatus.PENDING,
                Instant.now().plus(Duration.ofHours(1)));
        when(repository.findByApprovalId("apr-reject")).thenReturn(Optional.of(entity));

        store.reject("apr-reject", "u_p", "拒绝");

        ArgumentCaptor<ToolApprovalEntity> captor = ArgumentCaptor.forClass(ToolApprovalEntity.class);
        verify(repository).save(captor.capture());
        ToolApprovalEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(saved.getApprover()).isEqualTo("u_p");
        assertThat(saved.getComment()).isEqualTo("拒绝");
        assertThat(waiter.peek("apr-reject")).contains(ApprovalDecision.REJECTED);
    }

    // ==================== cleanupExpired ====================

    @Test
    @DisplayName("cleanupExpired: 有过期 PENDING/APPROVED → 批量标记 EXPIRED 并通知等待者")
    void cleanupExpired_marksExpired_When_StaleExists() {
        ToolApprovalEntity stale = buildEntity("apr-stale", "tool_a", ApprovalStatus.PENDING,
                Instant.now().minus(Duration.ofMinutes(1))); // 已过期
        when(repository.findByStatusInAndExpireAtBefore(anyList(), any(Instant.class)))
                .thenReturn(List.of(stale));
        when(repository.updateStatusByIdIn(eq(ApprovalStatus.EXPIRED), anyList()))
                .thenReturn(1);

        int cleaned = store.cleanupExpired();

        assertThat(cleaned).isEqualTo(1);
        verify(repository).updateStatusByIdIn(eq(ApprovalStatus.EXPIRED), anyList());
        assertThat(waiter.peek("apr-stale")).contains(ApprovalDecision.TIMEOUT);
    }

    @Test
    @DisplayName("cleanupExpired: 无过期记录 → 返回 0, 不调用 updateStatusByIdIn")
    void cleanupExpired_returnsZero_When_NoStale() {
        when(repository.findByStatusInAndExpireAtBefore(anyList(), any(Instant.class)))
                .thenReturn(List.of());

        int cleaned = store.cleanupExpired();

        assertThat(cleaned).isZero();
        verify(repository, never()).updateStatusByIdIn(any(), anyList());
    }

    // ==================== save (backward-compat) ====================

    @Test
    @DisplayName("save: approvalId 不存在 → 新建实体并保存")
    void save_insertsNewEntity_When_ApprovalIdAbsent() {
        ApprovalRecord record = new ApprovalRecord();
        record.setApprovalId("apr-new");
        record.setToolId("tool_s");
        record.setStatus(ApprovalRecord.STATUS_APPROVED);
        record.setPrimaryApprover("u_p");
        record.setExpireAt(Instant.now().plus(Duration.ofHours(1)));
        when(repository.findByApprovalId("apr-new")).thenReturn(Optional.empty());

        store.save(record);

        ArgumentCaptor<ToolApprovalEntity> captor = ArgumentCaptor.forClass(ToolApprovalEntity.class);
        verify(repository).save(captor.capture());
        ToolApprovalEntity saved = captor.getValue();
        assertThat(saved.getApprovalId()).isEqualTo("apr-new");
        assertThat(saved.getToolId()).isEqualTo("tool_s");
        assertThat(saved.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(saved.getApprover()).isEqualTo("u_p");
    }

    @Test
    @DisplayName("save: approvalId 已存在 → 更新现有实体")
    void save_updatesExistingEntity_When_ApprovalIdPresent() {
        ToolApprovalEntity existing = buildEntity("apr-existing", "tool_s", ApprovalStatus.PENDING,
                Instant.now().plus(Duration.ofHours(1)));
        when(repository.findByApprovalId("apr-existing")).thenReturn(Optional.of(existing));

        ApprovalRecord record = new ApprovalRecord();
        record.setApprovalId("apr-existing");
        record.setToolId("tool_s");
        record.setStatus(ApprovalRecord.STATUS_APPROVED);
        record.setPrimaryApprover("u_p");
        record.setExpireAt(Instant.now().plus(Duration.ofHours(2)));

        store.save(record);

        ArgumentCaptor<ToolApprovalEntity> captor = ArgumentCaptor.forClass(ToolApprovalEntity.class);
        verify(repository).save(captor.capture());
        ToolApprovalEntity saved = captor.getValue();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(saved.getApprover()).isEqualTo("u_p");
        assertThat(saved.getExpireAt()).isEqualTo(record.getExpireAt());
    }

    // ==================== helpers ====================

    private ToolApprovalEntity buildEntity(String approvalId, String toolId,
                                           ApprovalStatus status, Instant expireAt) {
        ToolApprovalEntity entity = new ToolApprovalEntity(
                approvalId, toolId, "task-test", 1L, "{}", "u_app", expireAt);
        entity.setStatus(status);
        return entity;
    }
}
