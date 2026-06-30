package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.ModelUsageLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CostMeterImpl unit tests (doc 02-api §6).
 */
@DisplayName("CostMeterImpl 成本计量器")
class CostMeterImplTest {

    private final CostMeterImpl meter = new CostMeterImpl();

    @Test
    @DisplayName("record 按 provider 单价计算 input+output 成本")
    void should_CalculateCost_When_RecordUsage() {
        ModelUsageLog log = new ModelUsageLog();
        log.setTenantId("tenant-1");
        log.setProviderCode("openai");
        log.setScene(Scene.GENERIC);
        log.setInputTokens(1000);
        log.setOutputTokens(500);

        double cost = meter.record(log);
        // openai: 0.005/1k input + 0.015/1k output
        // = 1000/1000 * 0.005 + 500/1000 * 0.015 = 0.005 + 0.0075 = 0.0125
        assertThat(cost).isEqualTo(0.0125, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(log.getTotalCostUsd()).isEqualTo(0.0125, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("getQuotaUsed 累加同 tenant 多次调用")
    void should_AccumulateCost_When_SameTenantMultipleCalls() {
        recordUsage("tenant-A", "openai", 1000, 0);
        recordUsage("tenant-A", "openai", 1000, 0);
        // 2 * (1000/1000 * 0.005) = 0.010
        assertThat(meter.getQuotaUsed("tenant-A")).isEqualTo(0.010, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("未记录的 tenant 返回 0")
    void should_ReturnZero_When_TenantUnknown() {
        assertThat(meter.getQuotaUsed("unknown-tenant")).isZero();
    }

    @Test
    @DisplayName("null log 或 null providerCode 返回 0 不报错")
    void should_ReturnZero_When_LogOrProviderNull() {
        assertThat(meter.record(null)).isZero();
        ModelUsageLog log = new ModelUsageLog();
        log.setProviderCode(null);
        assertThat(meter.record(log)).isZero();
    }

    @Test
    @DisplayName("注册自定义 provider 单价后按新单价计算")
    void should_UseCustomPricing_When_ProviderRegistered() {
        com.agent.modelgateway.model.ModelProvider custom = new com.agent.modelgateway.model.ModelProvider();
        custom.setProviderCode("custom-vendor");
        custom.setCostPerInput1k(0.001);
        custom.setCostPerOutput1k(0.002);
        meter.registerProvider(custom);

        ModelUsageLog log = new ModelUsageLog();
        log.setTenantId("tenant-custom");
        log.setProviderCode("custom-vendor");
        log.setInputTokens(2000);
        log.setOutputTokens(1000);
        double cost = meter.record(log);
        // 2000/1000 * 0.001 + 1000/1000 * 0.002 = 0.002 + 0.002 = 0.004
        assertThat(cost).isEqualTo(0.004, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("未注册单价的 provider 按零成本计算")
    void should_ChargeZero_When_ProviderPricingUnknown() {
        ModelUsageLog log = new ModelUsageLog();
        log.setTenantId("tenant-x");
        log.setProviderCode("unknown-vendor");
        log.setInputTokens(500);
        log.setOutputTokens(500);
        double cost = meter.record(log);
        assertThat(cost).isZero();
        assertThat(meter.getQuotaUsed("tenant-x")).isZero();
    }

    @Test
    @DisplayName("null tenantId 兜底到 default 累计")
    void should_AccumulateUnderDefault_When_TenantIdNull() {
        ModelUsageLog log = new ModelUsageLog();
        log.setTenantId(null);
        log.setProviderCode("openai");
        log.setInputTokens(1000);
        log.setOutputTokens(0);
        meter.record(log);
        assertThat(meter.getQuotaUsed("default")).isPositive();
        assertThat(meter.getQuotaUsed(null)).isZero();
    }

    @Test
    @DisplayName("registerProvider null 或 null code 安全跳过")
    void should_SkipRegistration_When_ProviderOrCodeNull() {
        meter.registerProvider(null);
        com.agent.modelgateway.model.ModelProvider nullCode = new com.agent.modelgateway.model.ModelProvider();
        nullCode.setProviderCode(null);
        meter.registerProvider(nullCode);
        // No crash; existing pricing still works
        ModelUsageLog log = new ModelUsageLog();
        log.setProviderCode("openai");
        log.setInputTokens(1000);
        log.setOutputTokens(0);
        assertThat(meter.record(log)).isPositive();
    }

    private void recordUsage(String tenantId, String provider, int input, int output) {
        ModelUsageLog log = new ModelUsageLog();
        log.setTenantId(tenantId);
        log.setProviderCode(provider);
        log.setInputTokens(input);
        log.setOutputTokens(output);
        meter.record(log);
    }
}
