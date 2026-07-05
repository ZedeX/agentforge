package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ApprovalStore;
import com.agent.tool.engine.api.RiskClassifier;
import com.agent.tool.engine.api.ToolCache;
import com.agent.tool.engine.api.ToolCallAuditor;
import com.agent.tool.engine.api.ToolGateway;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * F8 工具调用网关实现 (doc 05-tool-engine §11.1 / plan 05 T8).
 *
 * <p>10-step orchestration:
 * <ol>
 *   <li><b>validate</b>: toolId not blank, params present</li>
 *   <li><b>loadTool</b>: registry.findMeta → ToolMeta (404 if null) + enabled check (403 if disabled)</li>
 *   <li><b>acquirePermit</b>: rateLimiter.tryAcquire (429 if exhausted)</li>
 *   <li><b>assessRisk</b>: riskClassifier.classify → RiskAssessment</li>
 *   <li><b>recall</b>: recaller.recall — best-effort hints (catch + log)</li>
 *   <li><b>awaitApproval</b>: if requiresApproval → approvalStore.findValid (403 if missing)</li>
 *   <li><b>tryCache</b>: if cacheable + R1 + READ_ONLY → cache.get → return on hit</li>
 *   <li><b>execute</b>: dispatch to executor by ExecutorType (try/catch)</li>
 *   <li><b>clean</b>: resultCleaner.clean (truncate + redact)</li>
 *   <li><b>audit + cacheWrite</b>: always audit; cache.put on SUCCESS + cacheable</li>
 * </ol>
 * </p>
 *
 * <p>Uses the T7 two-tier cache API ({@code toolId + paramsHash + tenantId}).
 * {@link ParamsHasher} canonicalizes params (key-order independent SHA-256).</p>
 */
@Component
public class ToolGatewayImpl implements ToolGateway {

    private static final Logger log = LoggerFactory.getLogger(ToolGatewayImpl.class);

    /** Cache TTL for successful R1 + READ_ONLY results. */
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(300);

    private final ToolRegistry registry;
    private final RiskClassifier riskClassifier;
    private final ApprovalStore approvalStore;
    private final ToolCache cache;
    private final ToolCallAuditor auditor;
    private final ResultCleanerImpl resultCleaner;
    private final ToolSemanticRecaller recaller;
    private final RateLimiter rateLimiter;
    private final ToolEngineProperties properties;
    private final Map<ExecutorType, ToolExecutor> executorMap;

    /**
     * Primary constructor.
     *
     * @param executors list of {@link ToolExecutor} beans (one per ExecutorType)
     */
    @Autowired
    public ToolGatewayImpl(ToolRegistry registry,
                           RiskClassifier riskClassifier,
                           ApprovalStore approvalStore,
                           ToolCache cache,
                           ToolCallAuditor auditor,
                           ResultCleanerImpl resultCleaner,
                           @Autowired(required = false) ToolSemanticRecaller recaller,
                           RateLimiter rateLimiter,
                           ToolEngineProperties properties,
                           List<ToolExecutor> executors) {
        this.registry = registry;
        this.riskClassifier = riskClassifier;
        this.approvalStore = approvalStore;
        this.cache = cache;
        this.auditor = auditor;
        this.resultCleaner = resultCleaner;
        this.recaller = recaller;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.executorMap = new EnumMap<>(ExecutorType.class);
        for (ToolExecutor exec : executors) {
            ToolExecutor prev = executorMap.put(exec.type(), exec);
            if (prev != null) {
                log.warn("Duplicate ToolExecutor for type {}: {} overrides {}",
                        exec.type(), exec.getClass().getSimpleName(), prev.getClass().getSimpleName());
            }
        }
        log.info("ToolGatewayImpl initialized with {} executors: {}",
                executorMap.size(), executorMap.keySet());
    }

    /**
     * Test constructor: inject executors directly as varargs (skips Spring wiring).
     */
    public ToolGatewayImpl(ToolRegistry registry,
                           RiskClassifier riskClassifier,
                           ApprovalStore approvalStore,
                           ToolCache cache,
                           ToolCallAuditor auditor,
                           ResultCleanerImpl resultCleaner,
                           ToolSemanticRecaller recaller,
                           RateLimiter rateLimiter,
                           ToolEngineProperties properties,
                           ToolExecutor... executors) {
        this(registry, riskClassifier, approvalStore, cache, auditor, resultCleaner,
                recaller, rateLimiter, properties,
                java.util.Arrays.asList(executors));
    }

    @Override
    public ToolCallResult invoke(ToolCallRequest request) {
        // ---------- Step 1: validate ----------
        if (request == null || request.getToolId() == null || request.getToolId().isBlank()) {
            throw new ToolValidationException("请求或 toolId 不能为空");
        }
        String toolId = request.getToolId();
        String traceId = request.getTraceId() == null
                ? "trace-" + System.nanoTime() : request.getTraceId();
        String tenantId = request.getTenantId() == null ? "default" : request.getTenantId();
        String paramsHash = ParamsHasher.hash(request.getParams());
        long startNanos = System.nanoTime();
        log.info("工具调用入口: toolId={}, traceId={}, tenant={}", toolId, traceId, tenantId);

        // ---------- Step 2: loadTool ----------
        ToolMeta meta = registry.findMeta(toolId);
        if (meta == null) {
            ToolValidationException ex = new ToolValidationException("工具未注册: " + toolId);
            auditFailure(traceId, toolId, tenantId, request.getInputJson(), ex);
            throw ex;
        }
        if (!meta.isEnabled()) {
            ToolDisabledException ex = new ToolDisabledException(
                    "工具 [" + toolId + "] 已禁用");
            auditFailure(traceId, toolId, tenantId, request.getInputJson(), ex);
            throw ex;
        }

        // Schema validation
        ToolSchema inputSchema = registry.findInputSchema(toolId);
        if (!validateParams(inputSchema, request.getInputJson())) {
            ToolValidationException ex = new ToolValidationException(
                    "参数 schema 校验失败: " + toolId);
            auditFailure(traceId, toolId, tenantId, request.getInputJson(), ex);
            throw ex;
        }

        // ---------- Step 3: acquirePermit ----------
        if (!rateLimiter.tryAcquire(tenantId, toolId)) {
            ToolQuotaExhaustedException ex = new ToolQuotaExhaustedException(
                    "租户 " + tenantId + " 工具 [" + toolId + "] 限流触发");
            auditFailure(traceId, toolId, tenantId, request.getInputJson(), ex);
            throw ex;
        }

        // ---------- Step 4: assessRisk ----------
        ToolRiskLevel riskLevel = request.getRiskLevel();
        RiskAssessment assessment;
        if (riskLevel == null) {
            assessment = riskClassifier.classify(meta, request);
        } else {
            assessment = new RiskAssessment(riskLevel, riskLevel.requiresApproval(),
                    "caller-declared " + riskLevel);
        }
        riskLevel = assessment.getRiskLevel();

        // ---------- Step 5: recall (best-effort) ----------
        List<ToolRecallResult> hints = recallHints(meta, request);

        // ---------- Step 6: awaitApproval ----------
        if (assessment.isRequiresApproval()) {
            Optional<ApprovalRecord> approval = approvalStore.findValid(toolId);
            if (approval.isEmpty()) {
                ToolApprovalException ex = new ToolApprovalException(
                        ToolApprovalException.CODE_APPROVAL_REQUIRED,
                        "工具 [" + toolId + "] 缺少有效审批 (risk=" + riskLevel + ")");
                auditFailure(traceId, toolId, tenantId, request.getInputJson(), ex);
                throw ex;
            }
        }

        // ---------- Step 7: tryCache ----------
        boolean cacheable = meta.isCacheable()
                && riskLevel == ToolRiskLevel.R1
                && (meta.getSideEffect() == SideEffect.NONE
                    || meta.getSideEffect() == SideEffect.READ_ONLY);
        if (cacheable) {
            Optional<ToolCallResult> cached = cache.get(toolId, paramsHash, tenantId);
            if (cached.isPresent()) {
                log.info("缓存命中: toolId={}, hash={}", toolId, paramsHash);
                auditCacheHit(traceId, toolId, tenantId, request.getInputJson(), cached.get());
                return cached.get();
            }
        }

        // ---------- Step 8: execute ----------
        ToolCallResult result;
        try {
            ToolExecutor executor = executorMap.get(meta.getExecutorType());
            if (executor == null) {
                throw new ToolValidationException(
                        "工具 [" + toolId + "] 不支持的执行器类型: " + meta.getExecutorType());
            }
            long timeoutMs = meta.getTimeoutMs() > 0
                    ? meta.getTimeoutMs()
                    : properties.getSandbox().getExecTimeoutMs();
            result = executor.execute(meta, request, timeoutMs);
            Objects.requireNonNull(result, "executor returned null result");
        } catch (ToolEngineException e) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            auditResult(traceId, toolId, tenantId, request.getInputJson(),
                    null, ToolCallStatus.FAILED, durationMs, e.getMessage());
            throw e;
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            String errMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            auditResult(traceId, toolId, tenantId, request.getInputJson(),
                    null, ToolCallStatus.FAILED, durationMs, errMsg);
            throw new ToolEngineException("TOOL_EXECUTION_FAILED", 500,
                    "工具 [" + toolId + "] 执行异常: " + errMsg, e);
        }

        // ---------- Step 9: clean ----------
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        int maxTokens = properties.getCleaner().getMaxBytes() / 4;
        String cleanedOutput = resultCleaner.clean(result.getOutput(), maxTokens);
        result.setOutput(cleanedOutput);

        // ---------- Step 10: audit + cacheWrite ----------
        auditResult(traceId, toolId, tenantId, request.getInputJson(),
                result.getOutput(), result.getStatus(), durationMs, result.getErrorStack());

        if (cacheable && result.getStatus() == ToolCallStatus.SUCCESS) {
            cache.put(toolId, paramsHash, tenantId, result, DEFAULT_CACHE_TTL);
            log.debug("缓存写入: toolId={}, hash={}", toolId, paramsHash);
        }

        log.info("工具调用完成: toolId={}, status={}, duration={}ms",
                toolId, result.getStatus(), durationMs);
        return result;
    }

    // ============ Private helpers ============

    /** Step 5: recall hints (best-effort, never throws). */
    private List<ToolRecallResult> recallHints(ToolMeta meta, ToolCallRequest request) {
        if (recaller == null) {
            return List.of();
        }
        try {
            String query = meta.getName() + " " + (meta.getDescription() == null ? "" : meta.getDescription());
            List<ToolRecallResult> hints = recaller.recall(query, 3);
            if (!hints.isEmpty()) {
                log.debug("召回 hint: toolId={}, hints={}", meta.getToolId(), hints.size());
            }
            return hints;
        } catch (Exception e) {
            log.warn("召回失败 (best-effort, 跳过): toolId={}, err={}",
                    meta.getToolId(), e.getMessage());
            return List.of();
        }
    }

    /** Validate params against schema (required fields present in inputJson). */
    private boolean validateParams(ToolSchema schema, String inputJson) {
        if (schema == null || schema.getRequiredFields() == null || schema.getRequiredFields().isEmpty()) {
            return true;
        }
        if (inputJson == null) {
            return false;
        }
        for (String field : schema.getRequiredFields()) {
            if (field == null) {
                continue;
            }
            if (!inputJson.contains("\"" + field + "\"")) {
                return false;
            }
        }
        return true;
    }

    private void auditFailure(String traceId, String toolId, String tenantId,
                              String inputJson, Exception ex) {
        ToolCallAuditLog entry = new ToolCallAuditLog(traceId, toolId, ToolCallStatus.FAILED);
        entry.setTenantId(tenantId);
        entry.setInputJson(inputJson);
        entry.setErrorStack(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        try {
            auditor.audit(entry);
        } catch (Exception auditEx) {
            log.error("审计落库失败 (吞异常): traceId={}, err={}", traceId, auditEx.getMessage());
        }
    }

    private void auditResult(String traceId, String toolId, String tenantId, String inputJson,
                             String output, ToolCallStatus status, long durationMs, String errorStack) {
        ToolCallAuditLog entry = new ToolCallAuditLog(traceId, toolId, status);
        entry.setTenantId(tenantId);
        entry.setInputJson(inputJson);
        entry.setOutput(output);
        entry.setErrorStack(errorStack);
        try {
            auditor.audit(entry);
        } catch (Exception auditEx) {
            log.error("审计落库失败 (吞异常): traceId={}, err={}", traceId, auditEx.getMessage());
        }
    }

    private void auditCacheHit(String traceId, String toolId, String tenantId,
                               String inputJson, ToolCallResult cached) {
        ToolCallAuditLog entry = new ToolCallAuditLog(traceId, toolId, ToolCallStatus.CACHED);
        entry.setTenantId(tenantId);
        entry.setInputJson(inputJson);
        entry.setOutput(cached.getOutput());
        try {
            auditor.audit(entry);
        } catch (Exception auditEx) {
            log.error("审计落库失败 (cache hit, 吞异常): traceId={}, err={}",
                    traceId, auditEx.getMessage());
        }
    }
}
