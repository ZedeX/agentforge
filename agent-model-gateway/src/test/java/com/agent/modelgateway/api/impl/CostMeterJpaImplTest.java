package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.ModelProvider;
import com.agent.modelgateway.model.ModelUsageLog;
import com.agent.modelgateway.repository.ModelProviderRepository;
import com.agent.modelgateway.repository.ModelUsageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CostMeterJpaImpl integration tests (Plan 07 T12).
 *
 * <p>Verifies that the JPA-backed cost meter persists {@link ModelUsageLog} entries to the
 * database on {@code record}, aggregates quota from DB on {@code getQuotaUsed}, and loads
 * provider pricing from {@link ModelProviderRepository}.</p>
 */
@DisplayName("CostMeterJpaImpl JPA 持久化成本计量器测试")
@DataJpaTest
@Import(CostMeterJpaImpl.class)
@ActiveProfiles("test")
class CostMeterJpaImplTest {

    @Autowired
    private CostMeterJpaImpl meter;

    @Autowired
    private ModelUsageLogRepository usageLogRepository;

    @Autowired
    private ModelProviderRepository providerRepository;

    @BeforeEach
    void resetPricing() {
        // Reset provider table to defaults before each test to avoid state leakage
        // from loadProvidersFromDb / registerProvider across tests sharing the singleton bean.
        meter.resetPricing();
    }

    private ModelUsageLog buildLog(String tenantId, String provider, int input, int output) {
        ModelUsageLog log = new ModelUsageLog();
        log.setTraceId("trace-" + System.nanoTime());
        log.setTenantId(tenantId);
        log.setProviderCode(provider);
        log.setModelName("test-model");
        log.setScene(Scene.GENERIC);
        log.setInputTokens(input);
        log.setOutputTokens(output);
        log.setLatencyMs(100);
        log.setStatus("success");
        return log;
    }

    @Test
    @DisplayName("record 按 provider 默认单价计算 input+output 成本")
    void should_CalculateCost_When_RecordUsage() {
        ModelUsageLog log = buildLog("tenant-1", "openai", 1000, 500);

        double cost = meter.record(log);

        // openai: 0.005/1k input + 0.015/1k output = 0.005 + 0.0075 = 0.0125
        assertThat(cost).isEqualTo(0.0125, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(log.getTotalCostUsd()).isEqualTo(0.0125, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("record 持久化 ModelUsageLog 到数据库")
    void should_PersistUsageLog_When_Recorded() {
        ModelUsageLog log = buildLog("tenant-persist", "openai", 1000, 0);

        meter.record(log);

        assertThat(log.getId()).isNotNull();
        Optional<ModelUsageLog> found = usageLogRepository.findById(log.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getProviderCode()).isEqualTo("openai");
        assertThat(found.get().getInputCostUsd()).isEqualTo(0.005, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(found.get().getCreatedAt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("getQuotaUsed 从数据库聚合查询 tenant 累计成本")
    void should_AggregateFromDb_When_GetQuotaUsed() {
        meter.record(buildLog("tenant-agg", "openai", 1000, 0));
        meter.record(buildLog("tenant-agg", "openai", 1000, 0));

        double quota = meter.getQuotaUsed("tenant-agg");

        // 2 * (1000/1000 * 0.005) = 0.010
        assertThat(quota).isEqualTo(0.010, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("不同 tenant 的配额相互隔离")
    void should_IsolateQuota_When_DifferentTenants() {
        meter.record(buildLog("tenant-A", "openai", 1000, 0));
        meter.record(buildLog("tenant-B", "anthropic", 1000, 0));

        assertThat(meter.getQuotaUsed("tenant-A")).isEqualTo(0.005, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(meter.getQuotaUsed("tenant-B")).isEqualTo(0.003, org.assertj.core.data.Offset.offset(0.0001));
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
    @DisplayName("未注册单价的 provider 按零成本计算但仍持久化")
    void should_ChargeZero_When_ProviderPricingUnknown() {
        ModelUsageLog log = buildLog("tenant-x", "unknown-vendor", 500, 500);

        double cost = meter.record(log);

        assertThat(cost).isZero();
        assertThat(log.getTotalCostUsd()).isZero();
        assertThat(log.getId()).isNotNull();
    }

    @Test
    @DisplayName("getQuotaUsed null tenantId 返回 0")
    void should_ReturnZero_When_TenantIdNull() {
        meter.record(buildLog("some-tenant", "openai", 1000, 0));
        assertThat(meter.getQuotaUsed(null)).isZero();
    }

    @Test
    @DisplayName("registerProvider 自定义单价后按新单价计算")
    void should_UseCustomPricing_When_ProviderRegistered() {
        ModelProvider custom = new ModelProvider();
        custom.setProviderCode("custom-vendor");
        custom.setCostPerInput1k(0.001);
        custom.setCostPerOutput1k(0.002);
        meter.registerProvider(custom);

        ModelUsageLog log = buildLog("tenant-custom", "custom-vendor", 2000, 1000);
        double cost = meter.record(log);

        // 2000/1000 * 0.001 + 1000/1000 * 0.002 = 0.002 + 0.002 = 0.004
        assertThat(cost).isEqualTo(0.004, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("loadProvidersFromDb 从数据库加载 provider 单价覆盖默认值")
    void should_LoadPricingFromDb_When_LoadProvidersFromDbCalled() {
        // Pre-seed a provider in DB with different pricing than the default
        ModelProvider dbProvider = new ModelProvider("openai", "OpenAI", "https://api.openai.com/v1");
        dbProvider.setApiKeyRef("vault://secret/openai");
        dbProvider.setCostPerInput1k(0.01);   // default is 0.005, DB says 0.01
        dbProvider.setCostPerOutput1k(0.03);  // default is 0.015, DB says 0.03
        dbProvider.setEnabled(true);
        dbProvider.setWeight(1);
        dbProvider.setMaxQps(100);
        dbProvider.setMaxConcurrency(10);
        providerRepository.save(dbProvider);

        // Reload pricing from DB
        meter.loadProvidersFromDb();

        ModelUsageLog log = buildLog("tenant-db", "openai", 1000, 500);
        double cost = meter.record(log);

        // DB pricing: 1000/1000 * 0.01 + 500/1000 * 0.03 = 0.01 + 0.015 = 0.025
        assertThat(cost).isEqualTo(0.025, org.assertj.core.data.Offset.offset(0.0001));
    }
}
