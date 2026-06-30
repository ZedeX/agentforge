package com.agent.modelgateway.api;

import com.agent.modelgateway.enums.ProviderStatus;
import com.agent.modelgateway.model.ProviderHealth;

/**
 * Provider degradation manager (doc 02-api §7, PRD §二(二)4 failover).
 *
 * <p>Tracks per-provider health. On consecutive failures reaching threshold (default 3),
 * switches provider to DEGRADED + starts cooldown (default 5min). After cooldown,
 * transitions to RECOVERING + accepts probe request; on success → ACTIVE.</p>
 */
public interface ModelDegradationManager {

    /**
     * Record a successful call for the provider.
     */
    void recordSuccess(String providerCode);

    /**
     * Record a failed call for the provider.
     */
    void recordFailure(String providerCode);

    /**
     * Get current status for the provider.
     */
    ProviderStatus getStatus(String providerCode);

    /**
     * Get full health snapshot for the provider.
     */
    ProviderHealth getHealth(String providerCode);

    /**
     * Check if cooldown elapsed + transition to RECOVERING if appropriate.
     */
    void tickRecovery(String providerCode);
}
