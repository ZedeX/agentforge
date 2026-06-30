package com.agent.modelgateway.model;

/**
 * Route resolution result (doc 02-api §3 ModelRouter).
 *
 * <p>Holds the primary + fallback provider codes selected by the router.</p>
 */
public class RouteResult {

    private final String primaryProviderCode;
    private final String fallbackProviderCode;
    private final boolean cacheEligible;

    public RouteResult(String primaryProviderCode, String fallbackProviderCode, boolean cacheEligible) {
        this.primaryProviderCode = primaryProviderCode;
        this.fallbackProviderCode = fallbackProviderCode;
        this.cacheEligible = cacheEligible;
    }

    public String getPrimaryProviderCode() { return primaryProviderCode; }

    public String getFallbackProviderCode() { return fallbackProviderCode; }

    public boolean isCacheEligible() { return cacheEligible; }

    public boolean hasFallback() {
        return fallbackProviderCode != null && !fallbackProviderCode.isEmpty();
    }
}
