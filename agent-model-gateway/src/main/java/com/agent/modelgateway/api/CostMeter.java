package com.agent.modelgateway.api;

import com.agent.modelgateway.model.ModelUsageLog;

/**
 * Cost meter (doc 02-api §6, PRD §二(二)3 billing).
 *
 * <p>Records input/output token usage + USD cost per provider单价表, aggregates per tenant quota.
 * Skeleton stage: in-memory accumulation. JPA + Redis deferred to Plan 07 T12.</p>
 */
public interface CostMeter {

    /**
     * Record a usage log entry (calculates cost from provider单价表).
     *
     * @param log usage log with providerCode / inputTokens / outputTokens populated
     * @return total USD cost for this call (input + output)
     */
    double record(ModelUsageLog log);

    /**
     * Get accumulated USD cost for a tenant.
     *
     * @param tenantId tenant identifier
     * @return total USD consumed so far
     */
    double getQuotaUsed(String tenantId);
}
