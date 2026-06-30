package com.agent.modelgateway.model;

import com.agent.modelgateway.enums.Scene;

/**
 * Usage log entry (doc 01-database §5.3 model_usage_log table).
 *
 * <p>Skeleton stage: in-memory POJO. JPA Entity deferred to Plan 07 T12.</p>
 */
public class ModelUsageLog {

    private Long id;
    private String traceId;
    private String tenantId;
    private String providerCode;
    private String modelName;
    private Scene scene;
    private int inputTokens;
    private int outputTokens;
    private double inputCostUsd;
    private double outputCostUsd;
    private double totalCostUsd;
    private long latencyMs;
    private String status;
    private String errorCode;
    private long createdAt;

    public ModelUsageLog() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Scene getScene() { return scene; }
    public void setScene(Scene scene) { this.scene = scene; }

    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }

    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }

    public double getInputCostUsd() { return inputCostUsd; }
    public void setInputCostUsd(double inputCostUsd) { this.inputCostUsd = inputCostUsd; }

    public double getOutputCostUsd() { return outputCostUsd; }
    public void setOutputCostUsd(double outputCostUsd) { this.outputCostUsd = outputCostUsd; }

    public double getTotalCostUsd() { return totalCostUsd; }
    public void setTotalCostUsd(double totalCostUsd) { this.totalCostUsd = totalCostUsd; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
