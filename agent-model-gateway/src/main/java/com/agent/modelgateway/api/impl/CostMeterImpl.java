package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.api.CostMeter;
import com.agent.modelgateway.model.ModelProvider;
import com.agent.modelgateway.model.ModelUsageLog;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cost meter (doc 02-api §6).
 *
 * <p>Skeleton stage: maintains provider单价表 + tenant累计 cost in ConcurrentHashMap.
 * JPA + Redis deferred to Plan 07 T12.</p>
 *
 * <p>S-11: Internal calculations use BigDecimal to avoid floating-point precision
 * drift in cost accumulation. The external interface still returns double for
 * backward compatibility.</p>
 */
@Component
public class CostMeterImpl implements CostMeter {

    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final int COST_SCALE = 10;

    private final Map<String, ModelProvider> providerTable = new ConcurrentHashMap<>();
    /** S-11: BigDecimal for precise cost accumulation. */
    private final Map<String, BigDecimal> tenantQuotaUsed = new ConcurrentHashMap<>();

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
        BigDecimal inputCost = BigDecimal.ZERO;
        BigDecimal outputCost = BigDecimal.ZERO;
        if (provider != null) {
            BigDecimal inputTokens = BigDecimal.valueOf(log.getInputTokens());
            BigDecimal outputTokens = BigDecimal.valueOf(log.getOutputTokens());
            BigDecimal inputRate = BigDecimal.valueOf(provider.getCostPerInput1k());
            BigDecimal outputRate = BigDecimal.valueOf(provider.getCostPerOutput1k());
            inputCost = inputTokens.multiply(inputRate).divide(THOUSAND, COST_SCALE, RoundingMode.HALF_UP);
            outputCost = outputTokens.multiply(outputRate).divide(THOUSAND, COST_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal totalCost = inputCost.add(outputCost);
        double totalCostDouble = totalCost.doubleValue();
        log.setInputCostUsd(inputCost.doubleValue());
        log.setOutputCostUsd(outputCost.doubleValue());
        log.setTotalCostUsd(totalCostDouble);
        // Accumulate per tenant (BigDecimal addition, no drift)
        String tenantId = log.getTenantId() != null ? log.getTenantId() : "default";
        tenantQuotaUsed.merge(tenantId, totalCost, BigDecimal::add);
        return totalCostDouble;
    }

    @Override
    public double getQuotaUsed(String tenantId) {
        if (tenantId == null) {
            return 0.0;
        }
        return tenantQuotaUsed.getOrDefault(tenantId, BigDecimal.ZERO).doubleValue();
    }
}
