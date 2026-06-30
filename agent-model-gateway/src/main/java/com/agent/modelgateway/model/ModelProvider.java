package com.agent.modelgateway.model;

/**
 * Model provider config (doc 01-database §5.1 model_provider table).
 *
 * <p>Skeleton stage: in-memory POJO. JPA Entity annotation deferred to Plan 07 T2 deepening.</p>
 */
public class ModelProvider {

    private Long id;
    private String providerCode;
    private String providerName;
    private String apiBaseUrl;
    private String apiKeyRef;
    private boolean enabled = true;
    private int weight = 1;
    private int maxQps = 100;
    private int maxConcurrency = 10;
    /** input cost per 1k tokens in USD */
    private double costPerInput1k = 0.0;
    /** output cost per 1k tokens in USD */
    private double costPerOutput1k = 0.0;

    public ModelProvider() {
    }

    public ModelProvider(String providerCode, String providerName, String apiBaseUrl) {
        this.providerCode = providerCode;
        this.providerName = providerName;
        this.apiBaseUrl = apiBaseUrl;
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
}
