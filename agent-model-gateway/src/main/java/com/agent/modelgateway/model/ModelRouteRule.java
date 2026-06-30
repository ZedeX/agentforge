package com.agent.modelgateway.model;

import com.agent.modelgateway.enums.Scene;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Route rule (doc 01-database §5.2 model_route_rule table, Plan 07 T3).
 *
 * <p>Matches by scene + priority, selecting primary + fallback providers.
 * Lower priority value = higher precedence.</p>
 */
@Entity
@Table(name = "model_route_rule")
public class ModelRouteRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scene", nullable = false, length = 32)
    private Scene scene;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "from_provider_code", length = 64)
    private String fromProviderCode;

    @Column(name = "primary_provider_code", nullable = false, length = 64)
    private String primaryProviderCode;

    @Column(name = "fallback_provider_code", length = 64)
    private String fallbackProviderCode;

    @Column(name = "cost_ceiling_usd", nullable = false)
    private double costCeilingUsd = 0.0;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ModelRouteRule() {
    }

    public ModelRouteRule(Scene scene, int priority, String primaryProviderCode, String fallbackProviderCode) {
        this.scene = scene;
        this.priority = priority;
        this.primaryProviderCode = primaryProviderCode;
        this.fallbackProviderCode = fallbackProviderCode;
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

    public Scene getScene() { return scene; }
    public void setScene(Scene scene) { this.scene = scene; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getFromProviderCode() { return fromProviderCode; }
    public void setFromProviderCode(String fromProviderCode) { this.fromProviderCode = fromProviderCode; }

    public String getPrimaryProviderCode() { return primaryProviderCode; }
    public void setPrimaryProviderCode(String primaryProviderCode) { this.primaryProviderCode = primaryProviderCode; }

    public String getFallbackProviderCode() { return fallbackProviderCode; }
    public void setFallbackProviderCode(String fallbackProviderCode) { this.fallbackProviderCode = fallbackProviderCode; }

    public double getCostCeilingUsd() { return costCeilingUsd; }
    public void setCostCeilingUsd(double costCeilingUsd) { this.costCeilingUsd = costCeilingUsd; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
