package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ApprovalStore;
import com.agent.tool.engine.api.ResultCleaner;
import com.agent.tool.engine.api.RiskClassifier;
import com.agent.tool.engine.api.SandboxBorrower;
import com.agent.tool.engine.api.ToolCache;
import com.agent.tool.engine.api.ToolCallAuditor;
import com.agent.tool.engine.api.ToolGateway;
import com.agent.tool.engine.api.ToolRegistry;
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
import com.agent.tool.engine.model.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * F8 工具调用网关实现 (approval check -> sandbox/proxy exec -> result clean)。
 *
 * <p>骨架阶段组合 registry / riskClassifier / approvalStore / sandboxBorrower /
 * cache / auditor / resultCleaner 七大组件, 通过构造器注入。
 * 执行流程:
 * <ol>
 *   <li>缓存命中检查 (inputHash)</li>
 *   <li>查找 meta + inputSchema</li>
 *   <li>参数 schema 校验 (必填字段)</li>
 *   <li>租户配额检查</li>
 *   <li>风险分级 (R1/R2/R3)</li>
 *   <li>R3 审批检查 (requiresApproval)</li>
 *   <li>R3 沙箱借用 / 执行 / 回收</li>
 *   <li>输出清洗</li>
 *   <li>缓存写入</li>
 *   <li>审计日志</li>
 * </ol>
 * 生产实现应替换 doExecute 为真实工具执行器。</p>
 */
@Component
public class ToolGatewayImpl implements ToolGateway {

    private static final Logger log = LoggerFactory.getLogger(ToolGatewayImpl.class);

    private final ToolRegistry registry;
    private final RiskClassifier riskClassifier;
    private final ApprovalStore approvalStore;
    private final SandboxBorrower sandboxBorrower;
    private final ToolCache cache;
    private final ToolCallAuditor auditor;
    private final ResultCleaner resultCleaner;

    /** 租户配额计数器: tenantId -> 已用次数。 */
    private final Map<String, AtomicLong> quotaCounter = new ConcurrentHashMap<>();

    public ToolGatewayImpl(ToolRegistry registry,
                           RiskClassifier riskClassifier,
                           ApprovalStore approvalStore,
                           SandboxBorrower sandboxBorrower,
                           ToolCache cache,
                           ToolCallAuditor auditor,
                           ResultCleaner resultCleaner) {
        this.registry = registry;
        this.riskClassifier = riskClassifier;
        this.approvalStore = approvalStore;
        this.sandboxBorrower = sandboxBorrower;
        this.cache = cache;
        this.auditor = auditor;
        this.resultCleaner = resultCleaner;
    }

    @Override
    public ToolCallResult invoke(ToolCallRequest request) {
        if (request == null || request.getToolId() == null || request.getToolId().isBlank()) {
            throw new ToolValidationException("请求或 toolId 不能为空");
        }
        String toolId = request.getToolId();
        String traceId = request.getTraceId() == null ? "trace-" + System.nanoTime() : request.getTraceId();
        log.info("工具调用入口: toolId={}, traceId={}", toolId, traceId);

        // 1. 缓存命中检查
        String inputHash = request.getInputHash();
        if (inputHash != null && !inputHash.isBlank()) {
            Optional<ToolCallResult> cached = cache.lookup(inputHash);
            if (cached.isPresent()) {
                log.info("缓存命中: toolId={}, hash={}", toolId, inputHash);
                auditCall(traceId, toolId, request.getInputJson(), "cached", ToolCallStatus.CACHED, null);
                return cached.get();
            }
        }

        // 2. 查找 meta
        ToolMeta meta = registry.findMeta(toolId);
        if (meta == null) {
            ToolValidationException ex = new ToolValidationException("工具未注册: " + toolId);
            auditCall(traceId, toolId, request.getInputJson(), null, ToolCallStatus.FAILED, ex.getMessage());
            throw ex;
        }

        // 3. 参数 schema 校验
        ToolSchema inputSchema = registry.findInputSchema(toolId);
        if (!validateParams(inputSchema, request.getInputJson())) {
            ToolValidationException ex = new ToolValidationException("参数 schema 校验失败: " + toolId);
            auditCall(traceId, toolId, request.getInputJson(), null, ToolCallStatus.FAILED, ex.getMessage());
            throw ex;
        }

        // 4. 配额检查
        String tenantId = request.getTenantId() == null ? "default" : request.getTenantId();
        if (!checkQuota(tenantId, meta)) {
            ToolQuotaExhaustedException ex = new ToolQuotaExhaustedException(
                    "租户 " + tenantId + " 工具 [" + toolId + "] 配额已耗尽");
            auditCall(traceId, toolId, request.getInputJson(), null, ToolCallStatus.FAILED, ex.getMessage());
            throw ex;
        }

        // 5. 风险分级 (优先用 request 携带的, 否则实时分类)
        ToolRiskLevel riskLevel = request.getRiskLevel();
        if (riskLevel == null) {
            riskLevel = riskClassifier.classify(meta);
        }

        // 6. R3 审批检查
        if (riskLevel.requiresApproval()) {
            Optional<ApprovalRecord> approval = approvalStore.findValid(toolId);
            if (approval.isEmpty()) {
                ToolApprovalException ex = new ToolApprovalException(
                        ToolApprovalException.CODE_APPROVAL_REQUIRED,
                        "R3 工具 [" + toolId + "] 缺少有效审批");
                auditCall(traceId, toolId, request.getInputJson(), null, ToolCallStatus.FAILED, ex.getMessage());
                throw ex;
            }
        }

        // 7. 执行 (R3 借沙箱, finally 归还)
        String sandboxId = null;
        ToolCallResult result;
        try {
            if (riskLevel.requiresSandbox()) {
                sandboxId = sandboxBorrower.borrow();
                log.info("R3 沙箱执行: toolId={}, sandboxId={}", toolId, sandboxId);
            }
            result = doExecute(toolId, request, riskLevel, tenantId);
        } finally {
            if (sandboxId != null) {
                sandboxBorrower.recycle(sandboxId);
            }
        }

        // 8. 输出清洗
        String cleaned = resultCleaner.clean(result.getOutput(), 2000);
        result.setOutput(cleaned);

        // 9. 缓存写入
        if (inputHash != null && !inputHash.isBlank()) {
            cache.cache(inputHash, result);
        }

        // 10. 审计
        auditCall(traceId, toolId, request.getInputJson(), result.getOutput(), result.getStatus(), null);

        log.info("工具调用完成: toolId={}, status={}", toolId, result.getStatus());
        return result;
    }

    private boolean checkQuota(String tenantId, ToolMeta meta) {
        AtomicLong used = quotaCounter.computeIfAbsent(tenantId, k -> new AtomicLong(0));
        int limit = meta.getQuotaLimit();
        if (limit <= 0) {
            return true; // 无限制
        }
        return used.get() < limit;
    }

    private void incrementQuota(String tenantId) {
        quotaCounter.computeIfAbsent(tenantId, k -> new AtomicLong(0)).incrementAndGet();
    }

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

    private ToolCallResult doExecute(String toolId, ToolCallRequest request,
                                     ToolRiskLevel riskLevel, String tenantId) {
        // 骨架阶段模拟执行: 直接返回成功
        String output = "executed:" + toolId + ":" + (request.getInputJson() == null ? "" : request.getInputJson());
        ToolCallResult result = new ToolCallResult(toolId, output, ToolCallStatus.SUCCESS);
        result.setOutputTokens(Math.max(1, output.length() / 4));
        // 配额计数
        incrementQuota(tenantId);
        return result;
    }

    private void auditCall(String traceId, String toolId, String inputJson, String output,
                           ToolCallStatus status, String errorStack) {
        ToolCallAuditLog logEntry = new ToolCallAuditLog(traceId, toolId, status);
        logEntry.setInputJson(inputJson);
        logEntry.setOutput(output);
        logEntry.setErrorStack(errorStack);
        auditor.audit(logEntry);
    }

    /** 查询租户已用配额 (供测试 / 监控使用)。 */
    public long getQuotaUsed(String tenantId) {
        AtomicLong used = quotaCounter.get(tenantId);
        return used == null ? 0 : used.get();
    }
}
