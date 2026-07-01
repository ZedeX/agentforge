package com.agent.modelgateway.api.impl;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.modelgateway.api.CostMeter;
import com.agent.modelgateway.api.QuotaEnforcer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认配额校验器实现（Plan 07 T8/T12）。
 *
 * <p>Skeleton stage：基于 {@link CostMeter#getQuotaUsed} 累计读数 + 配置阈值校验。
 * Redis 日计数器实现 deferred to Plan 07 T12 深化。</p>
 *
 * <p>阈值来源：{@code model.gateway.quota.tenant.defaultUsd}（默认 100 USD）。
 * 超限抛 {@link BusinessException}({@link ErrorCode#QUOTA_EXCEEDED})，
 * details 携带 used / threshold / estimated 便于客户端诊断。</p>
 */
@Component
public class QuotaEnforcerImpl implements QuotaEnforcer {

    private final CostMeter costMeter;
    private final double quotaThresholdUsd;

    public QuotaEnforcerImpl(CostMeter costMeter,
                             @Value("${model.gateway.quota.tenant.defaultUsd:100}") double quotaThresholdUsd) {
        this.costMeter = costMeter;
        this.quotaThresholdUsd = quotaThresholdUsd;
    }

    @Override
    public void checkQuota(String tenantId, double estimatedCost) {
        if (tenantId == null || tenantId.isEmpty()) {
            return;
        }
        double used = costMeter.getQuotaUsed(tenantId);
        double projected = used + Math.max(0.0, estimatedCost);
        if (projected > quotaThresholdUsd) {
            Map<String, Object> details = new HashMap<>();
            details.put("used", used);
            details.put("estimated", estimatedCost);
            details.put("projected", projected);
            details.put("threshold", quotaThresholdUsd);
            throw new BusinessException(
                    ErrorCode.QUOTA_EXCEEDED,
                    "租户 " + tenantId + " 配额超限: projected=" + projected
                            + " > threshold=" + quotaThresholdUsd,
                    details);
        }
    }
}
