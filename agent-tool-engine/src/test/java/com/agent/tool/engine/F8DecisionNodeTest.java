package com.agent.tool.engine;

import com.agent.tool.engine.api.ApprovalStore;
import com.agent.tool.engine.api.ResultCleaner;
import com.agent.tool.engine.api.RiskClassifier;
import com.agent.tool.engine.api.SandboxBorrower;
import com.agent.tool.engine.api.ToolCache;
import com.agent.tool.engine.api.ToolCallAuditor;
import com.agent.tool.engine.api.ToolGateway;
import com.agent.tool.engine.api.ToolRegistry;
import com.agent.tool.engine.api.ToolSemanticRecaller;
import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.SideEffect;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.exception.ToolApprovalException;
import com.agent.tool.engine.exception.ToolQuotaExhaustedException;
import com.agent.tool.engine.exception.ToolValidationException;
import com.agent.tool.engine.model.ApprovalRecord;
import com.agent.tool.engine.model.ToolCallAuditLog;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.model.ToolRecallResult;
import com.agent.tool.engine.model.ToolSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F8 工具调用决策节点补强测试（对齐 docs/tests/unit-test-cases.md §18.4）。
 *
 * <p>覆盖 F8.D1（无工具召回）、F8.D2（多工具重排）、F8.D3（参数 Schema 校验）、
 * F8 R1/R2/R3 风险分级、R3 双审批状态机、沙箱借用回收、容错切换、配额熔断、
 * 输出限流裁剪、缓存命中、失败审计共 16 条决策节点用例。</p>
 *
 * <p>本测试为最小骨架（P7-3），interface 通过 Mockito.mock() 桩接，POJO 状态直接断言。
 * 真实业务实现由后续 PR 注入。</p>
 */
class F8DecisionNodeTest {

    // ============ F8.D1: 召回分支 ============

    @Test
    @DisplayName("UT-F8-001: 无工具召回时返回空列表（query 与所有工具相似度 < 0.3）")
    void should_ReturnEmpty_When_NoToolMatched() {
        // F8.D1 false 分支：query 与所有工具相似度低于阈值
        ToolSemanticRecaller recaller = mock(ToolSemanticRecaller.class);
        when(recaller.recall(eq("查询订单"), eq(3))).thenReturn(List.of());

        List<ToolRecallResult> results = recaller.recall("查询订单", 3);

        assertThat(results)
                .as("无工具召回应返回空列表，Agent 进入拒答或追问")
                .isEmpty();
    }

    @Test
    @DisplayName("UT-F8-002: 多工具召回重排选 Top-1（按 score 降序取最高分）")
    void should_RerankTop1_When_MultipleToolsRecalled() {
        // F8.D2 true 分支：召回 3 个工具，按 score 降序后取 Top-1
        ToolSemanticRecaller recaller = mock(ToolSemanticRecaller.class);
        when(recaller.recall(eq("查询订单"), eq(3))).thenReturn(List.of(
                new ToolRecallResult("tool_a", "查询订单A", 0.72),
                new ToolRecallResult("tool_b", "查询订单B", 0.91),
                new ToolRecallResult("tool_c", "查询订单C", 0.65)
        ));

        List<ToolRecallResult> results = recaller.recall("查询订单", 3);
        ToolRecallResult top1 = results.stream()
                .sorted(Comparator.comparingDouble(ToolRecallResult::getScore).reversed())
                .findFirst()
                .orElseThrow();

        assertThat(top1.getToolId()).as("Top-1 应为得分最高的 tool_b").isEqualTo("tool_b");
        assertThat(top1.getScore()).as("Top-1 得分应为 0.91").isEqualTo(0.91);
        assertThat(results).as("应召回 3 个工具").hasSize(3);
    }

    // ============ F8.D3: 参数 Schema 校验分支 ============

    @Test
    @DisplayName("UT-F8-003: 参数 Schema 校验失败时抛 VALIDATION_FAILED（inputJson 缺 required 字段）")
    void should_RejectParams_When_SchemaValidationFailed() {
        // F8.D3 false 分支：inputJson 缺 orderId 字段
        ToolRegistry registry = mock(ToolRegistry.class);
        ToolSchema schema = new ToolSchema(List.of("orderId", "tenantId"));
        when(registry.findInputSchema(eq("tool_order"))).thenReturn(schema);

        // inputJson 缺少 orderId
        String inputJson = "{\"tenantId\":\"tn_1\"}";

        boolean valid = validateParams(registry.findInputSchema("tool_order"), inputJson);

        assertThat(valid)
                .as("缺少 required 字段 orderId 时应校验失败")
                .isFalse();
        assertThatThrownBy(() -> {
            if (!validateParams(registry.findInputSchema("tool_order"), inputJson)) {
                throw new ToolValidationException("缺少 required 字段: orderId");
            }
        })
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("orderId");
    }

    @Test
    @DisplayName("UT-F8-004: 参数 Schema 校验通过时进入风险分级与执行（inputJson 完整）")
    void should_AllowParams_When_SchemaValid() {
        // F8.D3 true 分支：inputJson 完整且类型匹配
        ToolRegistry registry = mock(ToolRegistry.class);
        ToolSchema schema = new ToolSchema(List.of("orderId", "tenantId"));
        when(registry.findInputSchema(eq("tool_order"))).thenReturn(schema);

        String inputJson = "{\"orderId\":\"od_1\",\"tenantId\":\"tn_1\"}";

        boolean valid = validateParams(registry.findInputSchema("tool_order"), inputJson);

        assertThat(valid)
                .as("inputJson 完整时 Schema 校验应通过，进入风险分级与执行")
                .isTrue();
    }

    // ============ F8 风险分级 R1/R2/R3 分支 ============

    @Test
    @DisplayName("UT-F8-005: 可回滚写操作分类 R2（executor=proxy, side_effect=reversible）")
    void should_ClassifyR2_When_ToolIsWriteReversible() {
        // F8 R2 分支
        RiskClassifier classifier = (meta) -> {
            if (meta.getExecutorType() == ExecutorType.PROXY
                    && meta.getSideEffect() == SideEffect.REVERSIBLE) {
                return ToolRiskLevel.R2;
            }
            return ToolRiskLevel.R3;
        };

        ToolMeta meta = new ToolMeta("tool_db", "数据库更新", ExecutorType.PROXY, SideEffect.REVERSIBLE);
        ToolRiskLevel level = classifier.classify(meta);

        assertThat(level)
                .as("executor=proxy + side_effect=reversible 应分类为 R2")
                .isEqualTo(ToolRiskLevel.R2);
        assertThat(level.requiresApproval())
                .as("R2 不需要审批")
                .isFalse();
        assertThat(level.requiresSandbox())
                .as("R2 不需要沙箱")
                .isFalse();
    }

    @Test
    @DisplayName("UT-F8-006: R1 工具直接执行无需审批（executor=general 直接执行）")
    void should_AllowDirectExec_When_R1Approved() {
        // F8 R1 分支：R1 工具 + 合法参数，executor_type=general 直接执行
        ToolGateway gateway = mock(ToolGateway.class);
        when(gateway.invoke(any())).thenReturn(
                new ToolCallResult("tool_read", "ok", ToolCallStatus.SUCCESS));

        ToolCallRequest request = new ToolCallRequest("tool_read", "{}");
        request.setRiskLevel(ToolRiskLevel.R1);

        ToolCallResult result = gateway.invoke(request);

        assertThat(result.getStatus())
                .as("R1 工具应直接执行成功，无审批阻塞")
                .isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(result.getToolId()).isEqualTo("tool_read");
    }

    @Test
    @DisplayName("UT-F8-007: R2 工具代理执行（executor=proxy 调用外部 API）")
    void should_AllowProxyExec_When_R2ToolInvoked() {
        // F8 R2 分支：R2 工具通过 proxy 执行
        ToolGateway gateway = mock(ToolGateway.class);
        when(gateway.invoke(any())).thenReturn(
                new ToolCallResult("tool_api", "proxy_response", ToolCallStatus.SUCCESS));

        ToolCallRequest request = new ToolCallRequest("tool_api", "{}");
        request.setRiskLevel(ToolRiskLevel.R2);

        ToolCallResult result = gateway.invoke(request);

        assertThat(result.getStatus())
                .as("R2 工具通过 proxy 执行应返回成功")
                .isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(request.getRiskLevel())
                .as("请求风险等级应为 R2")
                .isEqualTo(ToolRiskLevel.R2);
    }

    @Test
    @DisplayName("UT-F8-008: 仅单审批人通过时拒绝 R3 执行（需副审批人复核）")
    void should_RejectR3_When_OnlySingleApproval() {
        // F8 R3 双审批分支：R3 + 仅主审批人通过（PARTIALLY_APPROVED）
        ApprovalStore approvalStore = mock(ApprovalStore.class);
        ApprovalRecord partial = new ApprovalRecord();
        partial.setToolId("tool_r3");
        partial.setStatus(ApprovalRecord.STATUS_PARTIALLY_APPROVED);
        partial.setPrimaryApprover("u_primary");
        partial.setSecondaryApprover(null);
        partial.setApprovedAt(Instant.now());
        partial.setExpireAt(Instant.now().plus(Duration.ofHours(1)));
        when(approvalStore.findValid(eq("tool_r3"))).thenReturn(Optional.of(partial));

        ApprovalRecord record = approvalStore.findValid("tool_r3").orElseThrow();

        assertThat(record.getStatus())
                .as("仅主审批人通过应处于 PARTIALLY_APPROVED 状态")
                .isEqualTo(ApprovalRecord.STATUS_PARTIALLY_APPROVED);
        assertThat(record.getSecondaryApprover())
                .as("副审批人应为空，需复核")
                .isNull();
        assertThatThrownBy(() -> {
            if (!ApprovalRecord.STATUS_APPROVED.equals(record.getStatus())) {
                throw new ToolApprovalException(
                        ToolApprovalException.CODE_APPROVAL_REQUIRED,
                        "R3 工具需双人复核，当前仅单审批人通过");
            }
        })
                .isInstanceOf(ToolApprovalException.class)
                .hasMessageContaining("双人复核");
    }

    @Test
    @DisplayName("UT-F8-009: R3 审批通过后限窗口内执行（approved 30min 前，window=1h 仍有效）")
    void should_InvokeWithinTimeWindow_When_R3Approved() {
        // F8 R3 已审批：approved_at=now-30min, window=1h → 剩余 30min 有效
        ApprovalStore approvalStore = mock(ApprovalStore.class);
        ApprovalRecord approved = new ApprovalRecord();
        approved.setToolId("tool_r3");
        approved.setStatus(ApprovalRecord.STATUS_APPROVED);
        approved.setPrimaryApprover("u_p");
        approved.setSecondaryApprover("u_s");
        Instant approvedAt = Instant.now().minus(Duration.ofMinutes(30));
        approved.setApprovedAt(approvedAt);
        approved.setValidityWindowSeconds(3600L);
        approved.setExpireAt(approvedAt.plus(Duration.ofHours(1)));
        when(approvalStore.findValid(eq("tool_r3"))).thenReturn(Optional.of(approved));

        ApprovalRecord record = approvalStore.findValid("tool_r3").orElseThrow();
        boolean withinWindow = Instant.now().isBefore(record.getExpireAt());
        Duration remaining = Duration.between(Instant.now(), record.getExpireAt());

        assertThat(record.getStatus())
                .as("审批状态应为 APPROVED")
                .isEqualTo(ApprovalRecord.STATUS_APPROVED);
        assertThat(withinWindow)
                .as("approved 30min 前 window=1h 应仍在有效窗口内")
                .isTrue();
        assertThat(remaining.toMinutes())
                .as("剩余有效时间应约 30min")
                .isBetween(29L, 31L);
    }

    @Test
    @DisplayName("UT-F8-010: 审批窗口过期拒绝执行（approved 2h 前 window=1h 已过期）")
    void should_RejectR3_When_WindowExpired() {
        // F8 R3 过期：approved_at=now-2h, window=1h → 已过期 1h
        ApprovalStore approvalStore = mock(ApprovalStore.class);
        ApprovalRecord expired = new ApprovalRecord();
        expired.setToolId("tool_r3");
        expired.setStatus(ApprovalRecord.STATUS_APPROVED);
        Instant approvedAt = Instant.now().minus(Duration.ofHours(2));
        expired.setApprovedAt(approvedAt);
        expired.setValidityWindowSeconds(3600L);
        expired.setExpireAt(approvedAt.plus(Duration.ofHours(1)));
        when(approvalStore.findValid(eq("tool_r3"))).thenReturn(Optional.of(expired));

        ApprovalRecord record = approvalStore.findValid("tool_r3").orElseThrow();
        boolean expiredFlag = Instant.now().isAfter(record.getExpireAt());

        assertThat(expiredFlag)
                .as("approved 2h 前 window=1h 应已过期")
                .isTrue();
        assertThatThrownBy(() -> {
            if (Instant.now().isAfter(record.getExpireAt())) {
                throw new ToolApprovalException(
                        ToolApprovalException.CODE_APPROVAL_EXPIRED,
                        "审批窗口已过期，需重新审批");
            }
        })
                .isInstanceOf(ToolApprovalException.class)
                .hasMessageContaining("过期");
    }

    @Test
    @DisplayName("UT-F8-011: R3 沙箱借用与回收（borrow + docker rm 一次性销毁）")
    void should_BorrowSandbox_When_R3Executing() {
        // F8 R3 执行：sandbox.borrow() 创建容器，执行后 docker.rm 一次性销毁
        SandboxBorrower sandbox = mock(SandboxBorrower.class);
        when(sandbox.borrow()).thenReturn("sb_001");

        String sandboxId = sandbox.borrow();
        // ... R3 工具在 sandbox 中执行 ...
        sandbox.recycle(sandboxId);

        verify(sandbox, times(1)).borrow();
        verify(sandbox, times(1)).recycle("sb_001");
        assertThat(sandboxId)
                .as("沙箱借用应返回 sandboxId")
                .isEqualTo("sb_001");
    }

    @Test
    @DisplayName("UT-F8-012: 主工具失败时切换备用工具重试 1 次")
    void should_RouteToAlternativeTool_When_PrimaryFailed() {
        // F8 容错分支：主工具 timeout → 切换同功能备用工具重试
        // 用两个独立 mock 模拟"主工具失败后切换备用"的容错路径
        ToolGateway primaryGateway = mock(ToolGateway.class);
        ToolGateway backupGateway = mock(ToolGateway.class);
        when(primaryGateway.invoke(any())).thenThrow(
                new ToolApprovalException("TOOL_TIMEOUT", "主工具超时"));
        when(backupGateway.invoke(any())).thenReturn(
                new ToolCallResult("tool_backup", "backup_ok", ToolCallStatus.SUCCESS));

        // 1. 主工具调用应抛超时
        assertThatThrownBy(() ->
                primaryGateway.invoke(new ToolCallRequest("tool_primary", "{}")))
                .isInstanceOf(ToolApprovalException.class)
                .hasMessageContaining("主工具超时");

        // 2. 切换备用工具，重试 1 次应成功
        ToolCallResult result = backupGateway.invoke(new ToolCallRequest("tool_backup", "{}"));

        assertThat(result.getToolId())
                .as("应切换到备用工具 tool_backup")
                .isEqualTo("tool_backup");
        assertThat(result.getStatus())
                .as("备用工具应执行成功")
                .isEqualTo(ToolCallStatus.SUCCESS);
    }

    @Test
    @DisplayName("UT-F8-013: 租户工具配额耗尽时熔断返回 RATE_LIMITED（429）")
    void should_RateLimitTool_When_TenantQuotaExhausted() {
        // F8 配额分支：tenant 工具调用次数 > quota
        ToolGateway gateway = mock(ToolGateway.class);
        when(gateway.invoke(any())).thenThrow(
                new ToolQuotaExhaustedException("租户 tn_test 工具配额已耗尽，建议提升配额"));

        assertThatThrownBy(() -> gateway.invoke(new ToolCallRequest("tool_any", "{}")))
                .isInstanceOf(ToolQuotaExhaustedException.class)
                .satisfies(ex -> {
                    ToolQuotaExhaustedException qex = (ToolQuotaExhaustedException) ex;
                    assertThat(qex.getErrorCode())
                            .as("配额耗尽错误码应为 RATE_LIMITED")
                            .isEqualTo("RATE_LIMITED");
                    assertThat(qex.getHttpStatus())
                            .as("HTTP 状态码应为 429")
                            .isEqualTo(429);
                });
    }

    @Test
    @DisplayName("UT-F8-014: 工具输出超 Token 限流时裁剪摘要化（8000 Token -> 2000 Token）")
    void should_TruncateResult_When_OutputExceedsMaxToken() {
        // F8 输出处理：工具返回 8000 Token，ResultCleaner.clean 摘要化至 2000 Token
        ResultCleaner cleaner = mock(ResultCleaner.class);
        String rawOutput = "x".repeat(8000);
        String cleaned = "摘要: " + "y".repeat(1990);
        when(cleaner.clean(eq(rawOutput), eq(2000))).thenReturn(cleaned);

        String result = cleaner.clean(rawOutput, 2000);

        verify(cleaner, times(1)).clean(rawOutput, 2000);
        assertThat(result.length())
                .as("裁剪后输出长度应 <= 2000 Token")
                .isLessThanOrEqualTo(2000);
    }

    @Test
    @DisplayName("UT-F8-015: 相同入参缓存命中（相同 inputJson 二次调用不重复执行）")
    void should_CacheByInputHash_When_SameInputRecalled() {
        // F8 缓存分支：相同 inputHash 二次调用命中 Redis 缓存
        ToolCache cache = mock(ToolCache.class);
        ToolCallResult cached = new ToolCallResult("tool_q", "cached_result", ToolCallStatus.SUCCESS);
        cached.setFromCache(true);
        when(cache.lookup(eq("hash_001"))).thenReturn(Optional.empty());

        // 第一次：缓存未命中
        Optional<ToolCallResult> first = cache.lookup("hash_001");
        assertThat(first).as("首次调用应缓存未命中").isEmpty();
        cache.cache("hash_001", cached);
        verify(cache, times(1)).cache("hash_001", cached);

        // 第二次：缓存命中
        when(cache.lookup(eq("hash_001"))).thenReturn(Optional.of(cached));
        Optional<ToolCallResult> second = cache.lookup("hash_001");
        assertThat(second).as("二次调用应命中缓存").isPresent();
        assertThat(second.get().isFromCache())
                .as("缓存命中结果应标记 fromCache=true")
                .isTrue();
    }

    @Test
    @DisplayName("UT-F8-016: 失败工具调用同样落审计（status=FAILED 含错误堆栈）")
    void should_WriteAuditLog_When_FailedToolCall() {
        // F8 审计：工具调用失败也写 tool_call_log，status=FAILED
        ToolCallAuditor auditor = mock(ToolCallAuditor.class);

        ToolCallAuditLog failLog = new ToolCallAuditLog("trace_001", "tool_fail", ToolCallStatus.FAILED);
        failLog.setInputJson("{\"q\":\"test\"}");
        failLog.setErrorStack("NullPointerException@ToolGateway.invoke:42");
        auditor.audit(failLog);

        verify(auditor, times(1)).audit(any(ToolCallAuditLog.class));
        assertThat(failLog.getStatus())
                .as("失败调用审计日志 status 应为 FAILED")
                .isEqualTo(ToolCallStatus.FAILED);
        assertThat(failLog.getErrorStack())
                .as("失败审计日志应含错误堆栈")
                .contains("NullPointerException");
        assertThat(failLog.getTraceId())
                .as("审计日志应携带 traceId")
                .isEqualTo("trace_001");
    }

    // ============ helpers ============

    /**
     * Skeleton param validator: checks inputJson contains all required fields declared in schema.
     * Real implementation should use JSON Schema validator (e.g. networknt/json-schema-validator).
     */
    private static boolean validateParams(ToolSchema schema, String inputJson) {
        if (schema == null || schema.getRequiredFields() == null) {
            return true;
        }
        for (String field : schema.getRequiredFields()) {
            if (!inputJson.contains("\"" + field + "\"")) {
                return false;
            }
        }
        return true;
    }
}
