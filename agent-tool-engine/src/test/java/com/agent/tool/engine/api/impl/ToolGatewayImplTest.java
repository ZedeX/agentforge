package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ApprovalStore;
import com.agent.tool.engine.api.RiskClassifier;
import com.agent.tool.engine.api.ToolCallAuditor;
import com.agent.tool.engine.api.ToolCache;
import com.agent.tool.engine.api.ToolRegistry;
import com.agent.tool.engine.api.ToolSemanticRecaller;
import com.agent.tool.engine.cache.ParamsHasher;
import com.agent.tool.engine.config.ToolEngineProperties;
import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.SideEffect;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.exception.ToolApprovalException;
import com.agent.tool.engine.exception.ToolDisabledException;
import com.agent.tool.engine.exception.ToolEngineException;
import com.agent.tool.engine.exception.ToolQuotaExhaustedException;
import com.agent.tool.engine.exception.ToolValidationException;
import com.agent.tool.engine.gateway.RateLimiter;
import com.agent.tool.engine.gateway.ToolExecutor;
import com.agent.tool.engine.model.ApprovalRecord;
import com.agent.tool.engine.model.RiskAssessment;
import com.agent.tool.engine.model.ToolCallAuditLog;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.model.ToolRecallResult;
import com.agent.tool.engine.model.ToolSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ToolGatewayImpl} unit tests (plan 05 T8 Red → Green).
 *
 * <p>13 test cases covering the 10-step orchestration:
 * validate / loadTool / acquirePermit / assessRisk / recall / awaitApproval /
 * tryCache / execute / clean / audit + cacheWrite.</p>
 *
 * <p>Strategy: mock ToolRegistry / RiskClassifier / ApprovalStore / ToolCache /
 * ToolCallAuditor / ToolSemanticRecaller / RateLimiter / ToolExecutor; use real
 * ResultCleanerImpl + ToolEngineProperties for end-to-end behavior verification.</p>
 */
class ToolGatewayImplTest {

    private ToolRegistry registry;
    private RiskClassifier riskClassifier;
    private ApprovalStore approvalStore;
    private ToolCache cache;
    private ToolCallAuditorImpl auditor;
    private ResultCleanerImpl resultCleaner;
    private ToolSemanticRecaller recaller;
    private RateLimiter rateLimiter;
    private ToolEngineProperties properties;
    private ToolExecutor httpExecutor;
    private ToolExecutor shellExecutor;

    private ToolGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        registry = mock(ToolRegistry.class);
        riskClassifier = mock(RiskClassifier.class);
        approvalStore = mock(ApprovalStore.class);
        cache = mock(ToolCache.class);
        auditor = new ToolCallAuditorImpl();
        resultCleaner = new ResultCleanerImpl();
        recaller = mock(ToolSemanticRecaller.class);
        rateLimiter = new RateLimiter(); // in-memory, no Redis
        properties = new ToolEngineProperties();
        httpExecutor = mock(ToolExecutor.class);
        when(httpExecutor.type()).thenReturn(ExecutorType.HTTP_API);
        shellExecutor = mock(ToolExecutor.class);
        when(shellExecutor.type()).thenReturn(ExecutorType.SHELL);

        when(recaller.recall(anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        gateway = new ToolGatewayImpl(registry, riskClassifier, approvalStore,
                cache, auditor, resultCleaner, recaller, rateLimiter, properties,
                httpExecutor, shellExecutor);
    }

    // ============ Helper ============

    private ToolMeta r1HttpTool(String toolId) {
        ToolMeta meta = new ToolMeta(toolId, toolId, ExecutorType.HTTP_API, SideEffect.READ_ONLY);
        meta.setEndpoint("http://example.com/api");
        meta.setCacheable(true);
        return meta;
    }

    private ToolMeta r3ShellTool(String toolId) {
        ToolMeta meta = new ToolMeta(toolId, toolId, ExecutorType.SHELL, SideEffect.DESTRUCTIVE);
        meta.setEndpoint("rm -rf /tmp/test");
        return meta;
    }

    private ToolCallRequest requestWithParams(String toolId, Map<String, Object> params) {
        ToolCallRequest req = new ToolCallRequest();
        req.setToolId(toolId);
        req.setTenantId("tn_test");
        req.setTraceId("trace_test");
        req.setInputJson("{\"q\":\"test\"}");
        req.setParams(params != null ? params : new HashMap<>());
        return req;
    }

    private ToolCallResult successResult(String toolId) {
        return new ToolCallResult(toolId, "ok output", ToolCallStatus.SUCCESS);
    }

    // ============ 13 Test Cases ============

    @Test
    @DisplayName("1. call_r1_readOnly_callsExecutor_returnsResult")
    void call_R1ReadOnly_CallsExecutor_ReturnsResult() {
        ToolMeta meta = r1HttpTool("tool_r1");
        when(registry.findMeta("tool_r1")).thenReturn(meta);
        when(registry.findInputSchema("tool_r1")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));
        when(httpExecutor.execute(eq(meta), any(), anyLong()))
                .thenReturn(successResult("tool_r1"));

        ToolCallResult result = gateway.invoke(requestWithParams("tool_r1", Map.of("q", "test")));

        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(result.getOutput()).contains("ok output");
        verify(httpExecutor, times(1)).execute(eq(meta), any(), anyLong());
        assertThat(auditor.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("2. call_r1_cacheHit_skipsExecutor")
    void call_R1CacheHit_SkipsExecutor() {
        ToolMeta meta = r1HttpTool("tool_c");
        when(registry.findMeta("tool_c")).thenReturn(meta);
        when(registry.findInputSchema("tool_c")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));
        ToolCallResult cached = successResult("tool_c");
        when(cache.get(eq("tool_c"), anyString(), eq("tn_test")))
                .thenReturn(Optional.of(cached));

        ToolCallResult result = gateway.invoke(requestWithParams("tool_c", Map.of("q", "same")));

        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        verify(httpExecutor, never()).execute(any(), any(), anyLong());
        verify(cache, never()).put(anyString(), anyString(), anyString(), any(), any());
        assertThat(auditor.count()).isEqualTo(1); // cache-hit audit
    }

    @Test
    @DisplayName("3. call_r2_noRecentApproval_blocksUntilApproved")
    void call_R2NoRecentApproval_ThrowsApprovalRequired() {
        ToolMeta meta = new ToolMeta("tool_r2", "r2tool", ExecutorType.HTTP_API, SideEffect.WRITE_LOCAL);
        meta.setEndpoint("http://example.com/write");
        when(registry.findMeta("tool_r2")).thenReturn(meta);
        when(registry.findInputSchema("tool_r2")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R2, true, "R2 requires approval"));
        when(approvalStore.findValid("tool_r2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gateway.invoke(requestWithParams("tool_r2", Map.of())))
                .isInstanceOf(ToolApprovalException.class);

        verify(httpExecutor, never()).execute(any(), any(), anyLong());
        assertThat(auditor.count()).isEqualTo(1); // failure audit
    }

    @Test
    @DisplayName("4. call_r3_skipsCache_callsExecutorInSandbox")
    void call_R3SkipsCache_CallsExecutorInSandbox() {
        ToolMeta meta = r3ShellTool("tool_r3");
        when(registry.findMeta("tool_r3")).thenReturn(meta);
        when(registry.findInputSchema("tool_r3")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R3, true, "R3"));
        ApprovalRecord approval = new ApprovalRecord();
        approval.setToolId("tool_r3");
        approval.setStatus(ApprovalRecord.STATUS_APPROVED);
        when(approvalStore.findValid("tool_r3")).thenReturn(Optional.of(approval));
        when(shellExecutor.execute(eq(meta), any(), anyLong()))
                .thenReturn(successResult("tool_r3"));

        ToolCallResult result = gateway.invoke(requestWithParams("tool_r3", Map.of()));

        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        verify(shellExecutor, times(1)).execute(eq(meta), any(), anyLong());
        // R3 not cacheable → cache.get never called
        verify(cache, never()).get(anyString(), anyString(), anyString());
        verify(cache, never()).put(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("5. call_rateLimited_throwsQuotaExhausted")
    void call_RateLimited_ThrowsQuotaExhausted() {
        ToolMeta meta = r1HttpTool("tool_rl");
        when(registry.findMeta("tool_rl")).thenReturn(meta);
        when(registry.findInputSchema("tool_rl")).thenReturn(new ToolSchema(List.of()));

        // Custom RateLimiter that always rejects
        RateLimiter rejectingLimiter = mock(RateLimiter.class);
        when(rejectingLimiter.tryAcquire(anyString(), anyString())).thenReturn(false);
        gateway = new ToolGatewayImpl(registry, riskClassifier, approvalStore,
                cache, auditor, resultCleaner, recaller, rejectingLimiter, properties,
                httpExecutor, shellExecutor);

        assertThatThrownBy(() -> gateway.invoke(requestWithParams("tool_rl", Map.of())))
                .isInstanceOf(ToolQuotaExhaustedException.class)
                .satisfies(ex -> {
                    ToolQuotaExhaustedException qex = (ToolQuotaExhaustedException) ex;
                    assertThat(qex.getHttpStatus()).isEqualTo(429);
                });

        verify(httpExecutor, never()).execute(any(), any(), anyLong());
        verify(riskClassifier, never()).classify(any(), any());
        assertThat(auditor.count()).isEqualTo(1); // failure audit
    }

    @Test
    @DisplayName("6. call_toolDisabled_throws")
    void call_ToolDisabled_Throws() {
        ToolMeta meta = r1HttpTool("tool_dis");
        meta.setEnabled(false);
        when(registry.findMeta("tool_dis")).thenReturn(meta);
        when(registry.findInputSchema("tool_dis")).thenReturn(new ToolSchema(List.of()));

        assertThatThrownBy(() -> gateway.invoke(requestWithParams("tool_dis", Map.of())))
                .isInstanceOf(ToolDisabledException.class)
                .satisfies(ex -> {
                    ToolDisabledException dex = (ToolDisabledException) ex;
                    assertThat(dex.getHttpStatus()).isEqualTo(403);
                    assertThat(dex.getErrorCode()).isEqualTo("TOOL_DISABLED");
                });

        verify(httpExecutor, never()).execute(any(), any(), anyLong());
        assertThat(auditor.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("7. call_validationFailed_throws")
    void call_ValidationFailed_Throws() {
        ToolMeta meta = r1HttpTool("tool_v");
        when(registry.findMeta("tool_v")).thenReturn(meta);
        when(registry.findInputSchema("tool_v"))
                .thenReturn(new ToolSchema(List.of("orderId")));

        ToolCallRequest req = requestWithParams("tool_v", Map.of());
        req.setInputJson("{\"missingField\":\"x\"}"); // missing "orderId"

        assertThatThrownBy(() -> gateway.invoke(req))
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("参数 schema 校验失败");

        verify(httpExecutor, never()).execute(any(), any(), anyLong());
        assertThat(auditor.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("8. call_executorFailed_recordsAuditAndThrows")
    void call_ExecutorFailed_RecordsAuditAndThrows() {
        ToolMeta meta = r1HttpTool("tool_fail");
        when(registry.findMeta("tool_fail")).thenReturn(meta);
        when(registry.findInputSchema("tool_fail")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));
        when(httpExecutor.execute(eq(meta), any(), anyLong()))
                .thenThrow(new ToolEngineException("TOOL_EXECUTION_FAILED", 500, "executor boom"));

        assertThatThrownBy(() -> gateway.invoke(requestWithParams("tool_fail", Map.of())))
                .isInstanceOf(ToolEngineException.class)
                .hasMessageContaining("executor boom");

        assertThat(auditor.count()).isEqualTo(1);
        assertThat(auditor.allLogs().get(0).getStatus()).isEqualTo(ToolCallStatus.FAILED);
        assertThat(auditor.allLogs().get(0).getErrorStack()).contains("executor boom");
    }

    @Test
    @DisplayName("9. call_executorTimeout_killsSandbox_recordsTimeout")
    void call_ExecutorTimeout_RecordsTimeout() {
        ToolMeta meta = r3ShellTool("tool_to");
        when(registry.findMeta("tool_to")).thenReturn(meta);
        when(registry.findInputSchema("tool_to")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R3, true, "R3"));
        ApprovalRecord approval = new ApprovalRecord();
        approval.setToolId("tool_to");
        approval.setStatus(ApprovalRecord.STATUS_APPROVED);
        when(approvalStore.findValid("tool_to")).thenReturn(Optional.of(approval));
        ToolCallResult timeoutResult = new ToolCallResult("tool_to", "", ToolCallStatus.TIMEOUT);
        timeoutResult.setErrorStack("sandbox exec timed out");
        when(shellExecutor.execute(eq(meta), any(), anyLong()))
                .thenReturn(timeoutResult);

        ToolCallResult result = gateway.invoke(requestWithParams("tool_to", Map.of()));

        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.TIMEOUT);
        assertThat(auditor.count()).isEqualTo(1);
        assertThat(auditor.allLogs().get(0).getStatus()).isEqualTo(ToolCallStatus.TIMEOUT);
    }

    @Test
    @DisplayName("10. call_writesAudit_always")
    void call_WritesAudit_Always() {
        ToolMeta meta = r1HttpTool("tool_audit");
        when(registry.findMeta("tool_audit")).thenReturn(meta);
        when(registry.findInputSchema("tool_audit")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));
        when(httpExecutor.execute(eq(meta), any(), anyLong()))
                .thenReturn(successResult("tool_audit"));

        gateway.invoke(requestWithParams("tool_audit", Map.of()));

        assertThat(auditor.count()).isEqualTo(1);
        ToolCallAuditLog log = auditor.allLogs().get(0);
        assertThat(log.getToolId()).isEqualTo("tool_audit");
        assertThat(log.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(log.getTenantId()).isEqualTo("tn_test");
        assertThat(log.getInputJson()).isNotNull();
    }

    @Test
    @DisplayName("11. call_appliesResultCleaner")
    void call_AppliesResultCleaner() {
        ToolMeta meta = r1HttpTool("tool_clean");
        when(registry.findMeta("tool_clean")).thenReturn(meta);
        when(registry.findInputSchema("tool_clean")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));
        // Output containing PII (phone) — cleaner should redact.
        ToolCallResult raw = new ToolCallResult("tool_clean",
                "联系 13800138000 获取结果", ToolCallStatus.SUCCESS);
        when(httpExecutor.execute(eq(meta), any(), anyLong())).thenReturn(raw);

        ToolCallResult result = gateway.invoke(requestWithParams("tool_clean", Map.of()));

        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(result.getOutput()).doesNotContain("13800138000");
        assertThat(result.getOutput()).contains("1**********");
    }

    @Test
    @DisplayName("12. call_populatesCacheOnSuccess")
    void call_PopulatesCacheOnSuccess() {
        ToolMeta meta = r1HttpTool("tool_cw");
        when(registry.findMeta("tool_cw")).thenReturn(meta);
        when(registry.findInputSchema("tool_cw")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));
        when(cache.get(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(httpExecutor.execute(eq(meta), any(), anyLong()))
                .thenReturn(successResult("tool_cw"));

        Map<String, Object> params = Map.of("q", "test");
        gateway.invoke(requestWithParams("tool_cw", params));

        String expectedHash = ParamsHasher.hash(params);
        verify(cache, times(1)).put(eq("tool_cw"), eq(expectedHash),
                eq("tn_test"), any(), any());
    }

    @Test
    @DisplayName("13. call_includesRecallerHints")
    void call_IncludesRecallerHints() {
        ToolMeta meta = r1HttpTool("tool_recall");
        meta.setDescription("weather query");
        when(registry.findMeta("tool_recall")).thenReturn(meta);
        when(registry.findInputSchema("tool_recall")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));
        when(recaller.recall(anyString(), eq("tool_recall"), any(), eq(3)))
                .thenReturn(List.of(
                        new ToolRecallResult("hint1", "weather", 0.9)
                ));
        when(httpExecutor.execute(eq(meta), any(), anyLong()))
                .thenReturn(successResult("tool_recall"));

        gateway.invoke(requestWithParams("tool_recall", Map.of()));

        // Verify recaller was called with the new 4-arg API (tenantId, toolId, params, topK).
        verify(recaller, times(1)).recall(anyString(), eq("tool_recall"), any(), eq(3));
    }

    // ============ Additional edge-case tests ============

    // R-02: Caller must not be able to downgrade risk level
    @Test
    @DisplayName("R-02: caller declaring R1 on R3 tool must still require approval")
    void call_CallerDeclaresR1OnR3Tool_StillRequiresApproval() {
        ToolMeta meta = r3ShellTool("tool_r3_downgrade");
        when(registry.findMeta("tool_r3_downgrade")).thenReturn(meta);
        when(registry.findInputSchema("tool_r3_downgrade")).thenReturn(new ToolSchema(List.of()));
        // RiskClassifier always says R3 for DESTRUCTIVE
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R3, true, "R3"));
        // No approval → should block
        when(approvalStore.findValid("tool_r3_downgrade")).thenReturn(Optional.empty());

        // Caller tries to declare R1 to bypass approval
        ToolCallRequest req = requestWithParams("tool_r3_downgrade", Map.of());
        req.setRiskLevel(ToolRiskLevel.R1);

        assertThatThrownBy(() -> gateway.invoke(req))
                .isInstanceOf(ToolApprovalException.class);

        verify(shellExecutor, never()).execute(any(), any(), anyLong());
        verify(riskClassifier, times(1)).classify(eq(meta), any()); // classify WAS called
    }

    @Test
    @DisplayName("R-02: caller declaring R3 on R1 tool should upgrade to R3 (safer)")
    void call_CallerDeclaresR3OnR1Tool_UpgradesToR3() {
        ToolMeta meta = r1HttpTool("tool_r1_upgrade");
        when(registry.findMeta("tool_r1_upgrade")).thenReturn(meta);
        when(registry.findInputSchema("tool_r1_upgrade")).thenReturn(new ToolSchema(List.of()));
        // RiskClassifier says R1
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));
        // R3 requires approval → no approval → should block
        when(approvalStore.findValid("tool_r1_upgrade")).thenReturn(Optional.empty());

        // Caller declares R3 (safer direction)
        ToolCallRequest req = requestWithParams("tool_r1_upgrade", Map.of());
        req.setRiskLevel(ToolRiskLevel.R3);

        assertThatThrownBy(() -> gateway.invoke(req))
                .isInstanceOf(ToolApprovalException.class);

        verify(httpExecutor, never()).execute(any(), any(), anyLong());
    }

    @Test
    @DisplayName("14. call_unregisteredTool_throwsValidation")
    void call_UnregisteredTool_ThrowsValidation() {
        // registry.findMeta returns null by default (no stubbing)

        assertThatThrownBy(() -> gateway.invoke(requestWithParams("tool_ghost", Map.of())))
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("未注册");

        verify(httpExecutor, never()).execute(any(), any(), anyLong());
        assertThat(auditor.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("15. call_nullRequest_throwsValidation")
    void call_NullRequest_ThrowsValidation() {
        assertThatThrownBy(() -> gateway.invoke(null))
                .isInstanceOf(ToolValidationException.class);
    }

    @Test
    @DisplayName("16. call_unsupportedExecutorType_throwsValidation")
    void call_UnsupportedExecutorType_ThrowsValidation() {
        ToolMeta meta = new ToolMeta("tool_mcp", "mcp", ExecutorType.MCP, SideEffect.NONE);
        when(registry.findMeta("tool_mcp")).thenReturn(meta);
        when(registry.findInputSchema("tool_mcp")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));

        // No MCP executor registered (only HTTP_API + SHELL in setUp)
        assertThatThrownBy(() -> gateway.invoke(requestWithParams("tool_mcp", Map.of())))
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("不支持的执行器类型");
    }

    // ============ R-11: Audit failure must not be silently swallowed ============

    /**
     * R-11: When auditor throws on the success path, the exception must propagate
     * (not be silently swallowed with only a log.error).
     */
    @Test
    @DisplayName("R-11: call_auditFailureOnSuccessPath_throwsAndDoesNotSwallow")
    void call_AuditFailureOnSuccessPath_ThrowsAndDoesNotSwallow() {
        ToolCallAuditor failingAuditor = mock(ToolCallAuditor.class);
        doThrow(new RuntimeException("audit DB down"))
                .when(failingAuditor).audit(any());
        ToolGatewayImpl gw = new ToolGatewayImpl(registry, riskClassifier, approvalStore,
                cache, failingAuditor, resultCleaner, recaller, rateLimiter, properties,
                httpExecutor, shellExecutor);

        ToolMeta meta = r1HttpTool("tool_audit_fail");
        when(registry.findMeta("tool_audit_fail")).thenReturn(meta);
        when(registry.findInputSchema("tool_audit_fail")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));
        when(httpExecutor.execute(eq(meta), any(), anyLong()))
                .thenReturn(successResult("tool_audit_fail"));

        assertThatThrownBy(() -> gw.invoke(requestWithParams("tool_audit_fail", Map.of())))
                .isInstanceOf(ToolEngineException.class)
                .hasMessageContaining("审计落库失败");
    }

    /**
     * R-11: When auditor throws on the failure path (auditFailure), the audit
     * exception must be attached as a suppressed exception on the original
     * business exception (not silently swallowed).
     */
    @Test
    @DisplayName("R-11: call_auditFailureOnErrorPath_addsSuppressedNotSwallowed")
    void call_AuditFailureOnErrorPath_AddsSuppressedNotSwallowed() {
        ToolCallAuditor failingAuditor = mock(ToolCallAuditor.class);
        doThrow(new RuntimeException("audit DB down"))
                .when(failingAuditor).audit(any());
        ToolGatewayImpl gw = new ToolGatewayImpl(registry, riskClassifier, approvalStore,
                cache, failingAuditor, resultCleaner, recaller, rateLimiter, properties,
                httpExecutor, shellExecutor);

        ToolMeta meta = r1HttpTool("tool_audit_suppress");
        when(registry.findMeta("tool_audit_suppress")).thenReturn(meta);
        when(registry.findInputSchema("tool_audit_suppress")).thenReturn(new ToolSchema(List.of()));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));
        when(httpExecutor.execute(eq(meta), any(), anyLong()))
                .thenThrow(new ToolEngineException("TOOL_EXECUTION_FAILED", 500, "executor boom"));

        assertThatThrownBy(() -> gw.invoke(requestWithParams("tool_audit_suppress", Map.of())))
                .isInstanceOf(ToolEngineException.class)
                .hasMessageContaining("executor boom")
                .satisfies(ex -> {
                    assertThat(ex.getSuppressed()).hasSize(1);
                    assertThat(ex.getSuppressed()[0]).isInstanceOf(ToolEngineException.class);
                    assertThat(ex.getSuppressed()[0].getMessage()).contains("审计落库失败");
                });
    }

    // ============ S-08: validateParams must parse JSON, not string-match ============

    /**
     * S-08: validateParams must parse JSON structure, not use contains().
     * The old contains() approach would match field values, not just keys.
     * E.g. {"value":"orderId"} would falsely pass validation for required field "orderId".
     */
    @Test
    @DisplayName("S-08: validateParams_rejectsFieldInValuePosition_notInKeyPosition")
    void validateParams_RejectsFieldInValuePosition() {
        ToolMeta meta = r1HttpTool("tool_s08_value");
        when(registry.findMeta("tool_s08_value")).thenReturn(meta);
        when(registry.findInputSchema("tool_s08_value"))
                .thenReturn(new ToolSchema(List.of("orderId")));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));

        // "orderId" appears as a VALUE, not a KEY → should fail validation
        ToolCallRequest req = requestWithParams("tool_s08_value", Map.of());
        req.setInputJson("{\"someField\":\"orderId\"}");

        assertThatThrownBy(() -> gateway.invoke(req))
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("参数 schema 校验失败");
    }

    /**
     * S-08: validateParams must correctly parse nested JSON and find required fields.
     */
    @Test
    @DisplayName("S-08: validateParams_acceptsValidJsonWithRequiredFields")
    void validateParams_AcceptsValidJsonWithRequiredFields() {
        ToolMeta meta = r1HttpTool("tool_s08_valid");
        when(registry.findMeta("tool_s08_valid")).thenReturn(meta);
        when(registry.findInputSchema("tool_s08_valid"))
                .thenReturn(new ToolSchema(List.of("query", "limit")));
        when(riskClassifier.classify(eq(meta), any()))
                .thenReturn(new RiskAssessment(ToolRiskLevel.R1, false, "R1"));
        when(httpExecutor.execute(eq(meta), any(), anyLong()))
                .thenReturn(successResult("tool_s08_valid"));

        ToolCallRequest req = requestWithParams("tool_s08_valid", Map.of());
        req.setInputJson("{\"query\":\"test\",\"limit\":10}");

        ToolCallResult result = gateway.invoke(req);
        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
    }

    /**
     * S-08: validateParams must reject malformed JSON.
     */
    @Test
    @DisplayName("S-08: validateParams_rejectsMalformedJson")
    void validateParams_RejectsMalformedJson() {
        ToolMeta meta = r1HttpTool("tool_s08_malformed");
        when(registry.findMeta("tool_s08_malformed")).thenReturn(meta);
        when(registry.findInputSchema("tool_s08_malformed"))
                .thenReturn(new ToolSchema(List.of("query")));

        ToolCallRequest req = requestWithParams("tool_s08_malformed", Map.of());
        req.setInputJson("{not valid json");

        assertThatThrownBy(() -> gateway.invoke(req))
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("参数 schema 校验失败");
    }
}
