package com.agent.modelgateway.model;

import com.agent.modelgateway.enums.Scene;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Usage log entry (doc 01-database §5.3 model_usage_log table, Plan 07 T12).
 *
 * <p>JPA Entity backing model_usage_log table. Stores per-call token usage and cost breakdown
 * (input/output separated). Sharded by month in production (model_usage_log_YYYYMM).</p>
 */
@Entity
@Table(name = "model_usage_log")
public class ModelUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "scene", nullable = false, length = 32)
    private Scene scene;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "input_cost_usd", nullable = false)
    private double inputCostUsd;

    @Column(name = "output_cost_usd", nullable = false)
    private double outputCostUsd;

    @Column(name = "total_cost_usd", nullable = false)
    private double totalCostUsd;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
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
