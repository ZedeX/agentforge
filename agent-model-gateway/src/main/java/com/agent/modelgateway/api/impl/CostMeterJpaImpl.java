package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.api.CostMeter;
import com.agent.modelgateway.model.ModelProvider;
import com.agent.modelgateway.model.ModelUsageLog;
import com.agent.modelgateway.repository.ModelProviderRepository;
import com.agent.modelgateway.repository.ModelUsageLogRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JPA-backed cost meter (Plan 07 T12 deepening).
 *
 * <p>Persists every {@link ModelUsageLog} to the database on {@link #record}, and queries
 * aggregated quota from DB via {@link ModelUsageLogRepository#sumTotalCostByTenantAndDateRange}.
 * Provider pricing is seeded with sane defaults, then overridden by enabled providers loaded
 * from {@link ModelProviderRepository} on startup.</p>
 *
 * <p>This bean is {@code @Primary} so it takes precedence over the in-memory
 * {@link CostMeterImpl} when JPA is active. The in-memory impl remains as a fallback for
 * pure unit-test contexts without a DataSource.</p>
 */
@Component
@Primary
public class CostMeterJpaImpl implements CostMeter {

    private final ModelUsageLogRepository usageLogRepository;
    private final ModelProviderRepository providerRepository;
    private final Map<String, ModelProvider> providerTable = new ConcurrentHashMap<>();

    public CostMeterJpaImpl(ModelUsageLogRepository usageLogRepository,
                            ModelProviderRepository providerRepository) {
        this.usageLogRepository = usageLogRepository;
        this.providerRepository = providerRepository;
        seedDefaultPricing();
    }

    private void seedDefaultPricing() {
        registerProvider(buildProvider("openai", 0.005, 0.015));
        registerProvider(buildProvider("openai-mini", 0.00015, 0.0006));
        registerProvider(buildProvider("anthropic", 0.003, 0.015));
        registerProvider(buildProvider("qwen-turbo", 0.0003, 0.0009));
        registerProvider(buildProvider("deepseek", 0.00014, 0.00028));
    }

    /**
     * Override default pricing with DB-loaded enabled providers.
     *
     * <p>Called by Spring after bean construction. Safe to call manually in tests after
     * pre-seeding provider rows to verify DB-driven pricing.</p>
     */
    @PostConstruct
    public void loadProvidersFromDb() {
        List<ModelProvider> dbProviders = providerRepository.findByEnabledTrue();
        for (ModelProvider p : dbProviders) {
            providerTable.put(p.getProviderCode(), p);
        }
    }

    private ModelProvider buildProvider(String code, double inputCost, double outputCost) {
        ModelProvider p = new ModelProvider();
        p.setProviderCode(code);
        p.setCostPerInput1k(inputCost);
        p.setCostPerOutput1k(outputCost);
        return p;
    }

    public void registerProvider(ModelProvider provider) {
        if (provider == null || provider.getProviderCode() == null) {
            return;
        }
        providerTable.put(provider.getProviderCode(), provider);
    }

    /**
     * Reset provider pricing table back to seeded defaults.
     *
     * <p>Useful in tests to clear any DB-loaded or manually-registered overrides between
     * test cases so the shared singleton bean does not leak state across tests.</p>
     */
    public void resetPricing() {
        providerTable.clear();
        seedDefaultPricing();
    }

    @Override
    public double record(ModelUsageLog log) {
        if (log == null || log.getProviderCode() == null) {
            return 0.0;
        }
        ModelProvider provider = providerTable.get(log.getProviderCode());
        double inputCost = 0.0;
        double outputCost = 0.0;
        if (provider != null) {
            inputCost = (log.getInputTokens() / 1000.0) * provider.getCostPerInput1k();
            outputCost = (log.getOutputTokens() / 1000.0) * provider.getCostPerOutput1k();
        }
        double totalCost = inputCost + outputCost;
        log.setInputCostUsd(inputCost);
        log.setOutputCostUsd(outputCost);
        log.setTotalCostUsd(totalCost);
        if (log.getCreatedAt() == 0) {
            log.setCreatedAt(System.currentTimeMillis());
        }
        usageLogRepository.save(log);
        return totalCost;
    }

    @Override
    public double getQuotaUsed(String tenantId) {
        if (tenantId == null) {
            return 0.0;
        }
        return usageLogRepository.sumTotalCostByTenantAndDateRange(tenantId, 0L, Long.MAX_VALUE);
    }
}
