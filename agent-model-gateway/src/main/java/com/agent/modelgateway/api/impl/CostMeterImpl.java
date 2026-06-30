package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.api.CostMeter;
import com.agent.modelgateway.model.ModelProvider;
import com.agent.modelgateway.model.ModelUsageLog;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cost meter (doc 02-api §6).
 *
 * <p>Skeleton stage: maintains provider单价表 + tenant累计 cost in ConcurrentHashMap.
 * JPA + Redis deferred to Plan 07 T12.</p>
 */
@Component
public class CostMeterImpl implements CostMeter {

    private final Map<String, ModelProvider> providerTable = new ConcurrentHashMap<>();
    private final Map<String, Double> tenantQuotaUsed = new ConcurrentHashMap<>();

    public CostMeterImpl() {
        // Seed default provider pricing (USD per 1k tokens)
        registerProvider(buildProvider("openai", 0.005, 0.015));
        registerProvider(buildProvider("openai-mini", 0.00015, 0.0006));
        registerProvider(buildProvider("anthropic", 0.003, 0.015));
        registerProvider(buildProvider("qwen-turbo", 0.0003, 0.0009));
        registerProvider(buildProvider("deepseek", 0.00014, 0.00028));
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
        // Accumulate per tenant
        String tenantId = log.getTenantId() != null ? log.getTenantId() : "default";
        tenantQuotaUsed.merge(tenantId, totalCost, Double::sum);
        return totalCost;
    }

    @Override
    public double getQuotaUsed(String tenantId) {
        if (tenantId == null) {
            return 0.0;
        }
        return tenantQuotaUsed.getOrDefault(tenantId, 0.0);
    }
}
