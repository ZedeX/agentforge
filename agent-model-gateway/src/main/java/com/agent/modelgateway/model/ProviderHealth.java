package com.agent.modelgateway.model;

import com.agent.modelgateway.enums.ProviderStatus;

/**
 * Provider health snapshot (doc 02-api §7 ModelDegradationManager).
 *
 * <p>Tracks consecutive failures, success rate, avg latency for failover decisions.</p>
 */
public class ProviderHealth {

    private String providerCode;
    private ProviderStatus status = ProviderStatus.ACTIVE;
    private int consecutiveFailures;
    private int totalRequests;
    private int successCount;
    private long totalLatencyMs;
    private long lastFailureAt;
    private long degradedAt;

    public ProviderHealth() {
    }

    public ProviderHealth(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }

    public ProviderStatus getStatus() { return status; }
    public void setStatus(ProviderStatus status) { this.status = status; }

    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public int getTotalRequests() { return totalRequests; }
    public void setTotalRequests(int totalRequests) { this.totalRequests = totalRequests; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public long getTotalLatencyMs() { return totalLatencyMs; }
    public void setTotalLatencyMs(long totalLatencyMs) { this.totalLatencyMs = totalLatencyMs; }

    public long getLastFailureAt() { return lastFailureAt; }
    public void setLastFailureAt(long lastFailureAt) { this.lastFailureAt = lastFailureAt; }

    public long getDegradedAt() { return degradedAt; }
    public void setDegradedAt(long degradedAt) { this.degradedAt = degradedAt; }

    /** Success rate in [0.0, 1.0]; 0.0 if no requests yet. */
    public double getSuccessRate() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) successCount / totalRequests;
    }

    /** Average latency in ms; 0 if no requests yet. */
    public double getAvgLatencyMs() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) totalLatencyMs / totalRequests;
    }
}
