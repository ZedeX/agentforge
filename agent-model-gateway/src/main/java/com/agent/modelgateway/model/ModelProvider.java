package com.agent.modelgateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Model provider config (doc 01-database §5.1 model_provider table, Plan 07 T2).
 *
 * <p>JPA Entity backing model_provider table. Stores provider pricing (input/output per 1k tokens),
 * QPS/concurrency limits, and routing weight. api_key_ref is a Vault path, never plaintext.</p>
 */
@Entity
@Table(name = "model_provider", uniqueConstraints = @UniqueConstraint(name = "uk_provider_code", columnNames = "provider_code"))
public class ModelProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_code", nullable = false, length = 64, unique = true)
    private String providerCode;

    @Column(name = "provider_name", nullable = false, length = 128)
    private String providerName;

    @Column(name = "api_base_url", nullable = false, length = 512)
    private String apiBaseUrl;

    @Column(name = "api_key_ref", nullable = false, length = 256)
    private String apiKeyRef;

    @Column(name = "cost_per_input_1k", nullable = false)
    private double costPerInput1k = 0.0;

    @Column(name = "cost_per_output_1k", nullable = false)
    private double costPerOutput1k = 0.0;

    @Column(name = "max_qps", nullable = false)
    private int maxQps = 100;

    @Column(name = "max_concurrency", nullable = false)
    private int maxConcurrency = 10;

    @Column(name = "weight", nullable = false)
    private int weight = 1;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ModelProvider() {
    }

    public ModelProvider(String providerCode, String providerName, String apiBaseUrl) {
        this.providerCode = providerCode;
        this.providerName = providerName;
        this.apiBaseUrl = apiBaseUrl;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getApiKeyRef() { return apiKeyRef; }
    public void setApiKeyRef(String apiKeyRef) { this.apiKeyRef = apiKeyRef; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }

    public int getMaxQps() { return maxQps; }
    public void setMaxQps(int maxQps) { this.maxQps = maxQps; }

    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }

    public double getCostPerInput1k() { return costPerInput1k; }
    public void setCostPerInput1k(double costPerInput1k) { this.costPerInput1k = costPerInput1k; }

    public double getCostPerOutput1k() { return costPerOutput1k; }
    public void setCostPerOutput1k(double costPerOutput1k) { this.costPerOutput1k = costPerOutput1k; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
