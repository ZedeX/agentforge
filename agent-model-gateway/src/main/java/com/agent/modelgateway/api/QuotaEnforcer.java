package com.agent.modelgateway.api;

/**
 * 租户配额校验器（Plan 07 T8/T12，doc 02-api §6 billing）。
 *
 * <p>调用前预检：从 {@link CostMeter#getQuotaUsed} 读取租户当前累计 USD，
 * 加上本次预估成本，若超出 {@code model.gateway.quota.tenant.defaultUsd} 阈值
 * 则抛 {@code BusinessException(QUOTA_EXCEEDED)}。</p>
 *
 * <p>设计说明：CostMeter 只负责记录实际用量，QuotaEnforcer 只负责配额校验，
 * 两者解耦便于后续替换为 Redis 计数器实现。</p>
 */
public interface QuotaEnforcer {

    /**
     * 校验租户配额是否允许本次调用。
     *
     * @param tenantId      租户标识
     * @param estimatedCost 本次调用预估 USD 成本
     * @throws com.agent.common.exception.BusinessException 当超限抛 QUOTA_EXCEEDED
     */
    void checkQuota(String tenantId, double estimatedCost);
}
