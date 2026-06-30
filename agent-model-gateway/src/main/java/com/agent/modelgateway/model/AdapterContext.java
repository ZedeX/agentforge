package com.agent.modelgateway.model;

import com.agent.modelgateway.enums.Scene;

/**
 * Adapter call context (doc 02-api §5 ModelProviderAdapter invocation).
 *
 * <p>Carries trace metadata + scene + timeout + tenant quota context across the adapter call.</p>
 */
public class AdapterContext {

    private final String traceId;
    private final String tenantId;
    private final Scene scene;
    private final long timeoutMs;
    private final boolean enablePromptCache;

    public AdapterContext(String traceId, String tenantId, Scene scene, long timeoutMs, boolean enablePromptCache) {
        this.traceId = traceId;
        this.tenantId = tenantId;
        this.scene = scene;
        this.timeoutMs = timeoutMs;
        this.enablePromptCache = enablePromptCache;
    }

    public String getTraceId() { return traceId; }

    public String getTenantId() { return tenantId; }

    public Scene getScene() { return scene; }

    public long getTimeoutMs() { return timeoutMs; }

    public boolean isEnablePromptCache() { return enablePromptCache; }
}
