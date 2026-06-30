package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.api.ModelDegradationManager;
import com.agent.modelgateway.enums.ProviderStatus;
import com.agent.modelgateway.model.ProviderHealth;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory degradation manager (doc 02-api §7).
 *
 * <p>State machine: ACTIVE → (consecutive failures ≥ threshold) → DEGRADED → (cooldown elapsed)
 * → RECOVERING → (probe success) → ACTIVE. Skeleton stage: in-memory. Redis deferred to Plan 07 T13.</p>
 */
@Component
public class ModelDegradationManagerImpl implements ModelDegradationManager {

    private static final int FAIL_THRESHOLD = 3;
    private static final long COOLDOWN_MS = 5L * 60 * 1000; // 5min

    private final Map<String, ProviderHealth> healthMap = new ConcurrentHashMap<>();

    @Override
    public void recordSuccess(String providerCode) {
        ProviderHealth health = getOrCreate(providerCode);
        health.setTotalRequests(health.getTotalRequests() + 1);
        health.setSuccessCount(health.getSuccessCount() + 1);
        // Reset consecutive failures on success
        health.setConsecutiveFailures(0);
        // If recovering, transition to ACTIVE
        if (health.getStatus() == ProviderStatus.RECOVERING) {
            health.setStatus(ProviderStatus.ACTIVE);
        }
    }

    @Override
    public void recordFailure(String providerCode) {
        ProviderHealth health = getOrCreate(providerCode);
        health.setTotalRequests(health.getTotalRequests() + 1);
        int newFailCount = health.getConsecutiveFailures() + 1;
        health.setConsecutiveFailures(newFailCount);
        long now = System.currentTimeMillis();
        health.setLastFailureAt(now);
        // If consecutive failures reach threshold, degrade
        if (newFailCount >= FAIL_THRESHOLD && health.getStatus() == ProviderStatus.ACTIVE) {
            health.setStatus(ProviderStatus.DEGRADED);
            health.setDegradedAt(now);
        }
    }

    @Override
    public ProviderStatus getStatus(String providerCode) {
        ProviderHealth health = healthMap.get(providerCode);
        return health != null ? health.getStatus() : ProviderStatus.ACTIVE;
    }

    @Override
    public ProviderHealth getHealth(String providerCode) {
        return getOrCreate(providerCode);
    }

    @Override
    public void tickRecovery(String providerCode) {
        ProviderHealth health = getOrCreate(providerCode);
        if (health.getStatus() == ProviderStatus.DEGRADED) {
            long elapsed = System.currentTimeMillis() - health.getDegradedAt();
            if (elapsed >= COOLDOWN_MS) {
                health.setStatus(ProviderStatus.RECOVERING);
            }
        }
    }

    private ProviderHealth getOrCreate(String providerCode) {
        if (providerCode == null) {
            providerCode = "unknown";
        }
        return healthMap.computeIfAbsent(providerCode, ProviderHealth::new);
    }
}
