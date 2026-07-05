package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ToolRegistry;
import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.SideEffect;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.exception.ToolApprovalException;
import com.agent.tool.engine.exception.ToolQuotaExhaustedException;
import com.agent.tool.engine.exception.ToolValidationException;
import com.agent.tool.engine.model.ApprovalRecord;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.model.ToolSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ToolGatewayImpl} 单元测试.
 *
 * <p>使用 mock {@link ToolRegistry} (T3 之后 ToolRegistryImpl 已迁移至 JPA 实现,
 * 不再有无参构造). 其余六大组件 (riskClassifier / approvalStore / sandboxBorrower /
 * cache / auditor / resultCleaner) 仍使用真实 in-memory Impl 组合测试,
 * 覆盖缓存命中 / 校验失败 / 配额耗尽 / 审批缺失 / R1 直执行 / R3 沙箱执行 7 条决策路径.</p>
 */
class ToolGatewayImplTest {

    private ToolRegistry registry;
    private RiskClassifierImpl riskClassifier;
    private ApprovalStoreImpl approvalStore;
    private SandboxBorrowerImpl sandboxBorrower;
    private ToolCacheImpl cache;
    private ToolCallAuditorImpl auditor;
    private ResultCleanerImpl resultCleaner;
    private ToolGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        registry = mock(ToolRegistry.class);
        riskClassifier = new RiskClassifierImpl();
        approvalStore = new ApprovalStoreImpl();
        sandboxBorrower = new SandboxBorrowerImpl();
        cache = new ToolCacheImpl();
        auditor = new ToolCallAuditorImpl();
        resultCleaner = new ResultCleanerImpl();
        gateway = new ToolGatewayImpl(registry, riskClassifier, approvalStore,
                sandboxBorrower, cache, auditor, resultCleaner);
    }

    @Test
    @DisplayName("R1 工具合法调用: 直接执行成功")
    void should_InvokeSuccess_When_R1ToolValid() {
        ToolMeta meta = new ToolMeta("tool_read", "read", ExecutorType.GENERAL, SideEffect.NONE);
        ToolSchema schema = new ToolSchema(List.of());
        when(registry.findMeta("tool_read")).thenReturn(meta);
        when(registry.findInputSchema("tool_read")).thenReturn(schema);

        ToolCallRequest request = new ToolCallRequest("tool_read", "{\"q\":\"test\"}");
        request.setTenantId("tn_1");
        request.setTraceId("trace_1");

        ToolCallResult result = gateway.invoke(request);

        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(result.getToolId()).isEqualTo("tool_read");
        assertThat(auditor.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("未注册工具: 抛 ToolValidationException")
    void should_ThrowValidation_When_ToolNotRegistered() {
        // mock 默认返回 null, 无需 stub
        ToolCallRequest request = new ToolCallRequest("tool_ghost", "{}");

        assertThatThrownBy(() -> gateway.invoke(request))
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("未注册");
    }

    @Test
    @DisplayName("参数 schema 校验失败 (缺必填字段): 抛 ToolValidationException")
    void should_ThrowValidation_When_SchemaValidationFails() {
        ToolMeta meta = new ToolMeta("tool_v", "validate", ExecutorType.GENERAL, SideEffect.NONE);
        ToolSchema schema = new ToolSchema(List.of("orderId"));
        when(registry.findMeta("tool_v")).thenReturn(meta);
        when(registry.findInputSchema("tool_v")).thenReturn(schema);

        ToolCallRequest request = new ToolCallRequest("tool_v", "{\"tenantId\":\"tn_1\"}");

        assertThatThrownBy(() -> gateway.invoke(request))
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("参数 schema");
    }

    @Test
    @DisplayName("R3 工具缺少审批: 抛 ToolApprovalException (CODE_APPROVAL_REQUIRED)")
    void should_ThrowApproval_When_R3MissingApproval() {
        ToolMeta meta = new ToolMeta("tool_r3", "danger", ExecutorType.SANDBOX, SideEffect.IRREVERSIBLE);
        ToolSchema schema = new ToolSchema(List.of());
        when(registry.findMeta("tool_r3")).thenReturn(meta);
        when(registry.findInputSchema("tool_r3")).thenReturn(schema);

        ToolCallRequest request = new ToolCallRequest("tool_r3", "{}");

        assertThatThrownBy(() -> gateway.invoke(request))
                .isInstanceOf(ToolApprovalException.class)
                .satisfies(ex -> {
                    ToolApprovalException aex = (ToolApprovalException) ex;
                    assertThat(aex.getErrorCode()).isEqualTo(ToolApprovalException.CODE_APPROVAL_REQUIRED);
                });
    }

    @Test
    @DisplayName("租户配额耗尽: 抛 ToolQuotaExhaustedException (429)")
    void should_ThrowQuota_When_QuotaExhausted() {
        ToolMeta meta = new ToolMeta("tool_q", "q", ExecutorType.GENERAL, SideEffect.NONE);
        meta.setQuotaLimit(1);
        ToolSchema schema = new ToolSchema(List.of());
        when(registry.findMeta("tool_q")).thenReturn(meta);
        when(registry.findInputSchema("tool_q")).thenReturn(schema);

        ToolCallRequest req1 = new ToolCallRequest("tool_q", "{}");
        req1.setTenantId("tn_quota");
        gateway.invoke(req1); // 第一次成功, 用尽配额

        ToolCallRequest req2 = new ToolCallRequest("tool_q", "{}");
        req2.setTenantId("tn_quota");

        assertThatThrownBy(() -> gateway.invoke(req2))
                .isInstanceOf(ToolQuotaExhaustedException.class)
                .satisfies(ex -> {
                    ToolQuotaExhaustedException qex = (ToolQuotaExhaustedException) ex;
                    assertThat(qex.getHttpStatus()).isEqualTo(429);
                    assertThat(qex.getErrorCode()).isEqualTo("RATE_LIMITED");
                });
        assertThat(gateway.getQuotaUsed("tn_quota")).isEqualTo(1);
    }

    @Test
    @DisplayName("相同 inputHash 二次调用: 命中缓存, fromCache=true")
    void should_ReturnCachedResult_When_CacheHit() {
        ToolMeta meta = new ToolMeta("tool_c", "cacheable", ExecutorType.GENERAL, SideEffect.NONE);
        ToolSchema schema = new ToolSchema(List.of());
        when(registry.findMeta("tool_c")).thenReturn(meta);
        when(registry.findInputSchema("tool_c")).thenReturn(schema);

        ToolCallRequest request = new ToolCallRequest("tool_c", "{\"q\":\"same\"}");
        request.setInputHash("hash_same");

        ToolCallResult r1 = gateway.invoke(request);
        assertThat(r1.isFromCache()).isFalse();

        ToolCallResult r2 = gateway.invoke(request);
        assertThat(r2.isFromCache()).isTrue();
        assertThat(r2.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(auditor.count()).isEqualTo(2); // 一次执行 + 一次缓存命中
    }

    @Test
    @DisplayName("R3 工具审批通过: 沙箱借用并回收, 执行成功")
    void should_InvokeR3Success_When_ApprovedAndSandboxBorrowed() {
        ToolMeta meta = new ToolMeta("tool_r3ok", "exec", ExecutorType.SANDBOX, SideEffect.IRREVERSIBLE);
        ToolSchema schema = new ToolSchema(List.of());
        when(registry.findMeta("tool_r3ok")).thenReturn(meta);
        when(registry.findInputSchema("tool_r3ok")).thenReturn(schema);

        ApprovalRecord approval = new ApprovalRecord();
        approval.setToolId("tool_r3ok");
        approval.setStatus(ApprovalRecord.STATUS_APPROVED);
        approval.setPrimaryApprover("u_p");
        approval.setSecondaryApprover("u_s");
        approval.setExpireAt(Instant.now().plus(Duration.ofHours(1)));
        approvalStore.save(approval);

        ToolCallRequest request = new ToolCallRequest("tool_r3ok", "{}");
        request.setTenantId("tn_r3");

        ToolCallResult result = gateway.invoke(request);

        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(sandboxBorrower.activeCount()).isZero(); // 沙箱已回收
        assertThat(auditor.count()).isEqualTo(1);
    }
}
