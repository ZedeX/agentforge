package com.agent.modelgateway.repository;

import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.ModelUsageLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ModelUsageLogRepository JPA integration tests (Plan 07 T12).
 *
 * <p>Verifies usage log lookup by trace/tenant and tenant-level cost aggregation
 * for quota enforcement and billing reports.</p>
 */
@DisplayName("ModelUsageLogRepository JPA 仓储测试")
@DataJpaTest
@ActiveProfiles("test")
class ModelUsageLogRepositoryTest {

    @Autowired
    private ModelUsageLogRepository repository;

    private ModelUsageLog buildLog(String traceId, String tenantId, String providerCode,
                                   String model, Scene scene, int inputTokens, int outputTokens,
                                   double totalCost, long createdAt) {
        ModelUsageLog log = new ModelUsageLog();
        log.setTraceId(traceId);
        log.setTenantId(tenantId);
        log.setProviderCode(providerCode);
        log.setModelName(model);
        log.setScene(scene);
        log.setInputTokens(inputTokens);
        log.setOutputTokens(outputTokens);
        log.setInputCostUsd(totalCost * 0.4);
        log.setOutputCostUsd(totalCost * 0.6);
        log.setTotalCostUsd(totalCost);
        log.setLatencyMs(500L);
        log.setStatus("SUCCESS");
        log.setErrorCode(null);
        log.setCreatedAt(createdAt);
        return log;
    }

    @Test
    @DisplayName("findByTraceId 按 trace 查询返回所有调用记录")
    void should_FindByTraceId_When_Exists() {
        repository.save(buildLog("trace-001", "tenant-1", "openai", "gpt-4o", Scene.GENERIC, 100, 50, 0.5, 1000L));
        repository.save(buildLog("trace-001", "tenant-1", "anthropic", "claude-opus", Scene.AUDIT, 200, 80, 0.8, 2000L));
        repository.save(buildLog("trace-002", "tenant-2", "openai", "gpt-4o", Scene.GENERIC, 50, 20, 0.2, 3000L));

        List<ModelUsageLog> logs = repository.findByTraceId("trace-001");

        assertThat(logs).hasSize(2);
        assertThat(logs).allSatisfy(log -> assertThat(log.getTraceId()).isEqualTo("trace-001"));
    }

    @Test
    @DisplayName("findByTenantIdAndCreatedAtBetween 按时间范围查询租户调用")
    void should_FindByTenantAndTimeRange_When_InRange() {
        repository.save(buildLog("t1", "tenant-1", "openai", "gpt-4o", Scene.GENERIC, 100, 50, 0.5, 1000L));
        repository.save(buildLog("t2", "tenant-1", "openai", "gpt-4o", Scene.GENERIC, 200, 100, 1.0, 5000L));
        repository.save(buildLog("t3", "tenant-1", "openai", "gpt-4o", Scene.GENERIC, 50, 20, 0.2, 15000L));
        repository.save(buildLog("t4", "tenant-2", "openai", "gpt-4o", Scene.GENERIC, 10, 5, 0.05, 2000L));

        List<ModelUsageLog> logsRange = repository.findByTenantIdAndCreatedAtBetween("tenant-1", 1000L, 10000L);

        assertThat(logsRange).hasSize(2);
        assertThat(logsRange).allSatisfy(log -> {
            assertThat(log.getTenantId()).isEqualTo("tenant-1");
            assertThat(log.getCreatedAt()).isBetween(1000L, 10000L);
        });
    }

    @Test
    @DisplayName("sumTotalCostByTenantAndDateRange 聚合租户在时间范围内的总成本")
    void should_SumTotalCost_When_TenantAndDateRangeGiven() {
        repository.save(buildLog("t1", "tenant-1", "openai", "gpt-4o", Scene.GENERIC, 100, 50, 0.5, 1000L));
        repository.save(buildLog("t2", "tenant-1", "openai", "gpt-4o", Scene.GENERIC, 200, 100, 1.0, 2000L));
        repository.save(buildLog("t3", "tenant-1", "openai", "gpt-4o", Scene.GENERIC, 50, 20, 0.2, 15000L));
        repository.save(buildLog("t4", "tenant-2", "openai", "gpt-4o", Scene.GENERIC, 10, 5, 0.05, 3000L));

        double total = repository.sumTotalCostByTenantAndDateRange("tenant-1", 0L, 10000L);

        assertThat(total).isEqualTo(1.5);  // 0.5 + 1.0
    }

    @Test
    @DisplayName("sumTotalCostByTenantAndDateRange 无记录时返回 0.0 (COALESCE)")
    void should_ReturnZero_When_NoRecordsForTenant() {
        repository.save(buildLog("t1", "tenant-1", "openai", "gpt-4o", Scene.GENERIC, 100, 50, 0.5, 1000L));

        double total = repository.sumTotalCostByTenantAndDateRange("tenant-nonexistent", 0L, 10000L);

        assertThat(total).isEqualTo(0.0);
    }

    @Test
    @DisplayName("countByTenantIdAndCreatedAtBetween 统计租户在时间范围内的调用次数")
    void should_CountCalls_When_TenantAndDateRangeGiven() {
        repository.save(buildLog("t1", "tenant-1", "openai", "gpt-4o", Scene.GENERIC, 100, 50, 0.5, 1000L));
        repository.save(buildLog("t2", "tenant-1", "openai", "gpt-4o", Scene.GENERIC, 200, 100, 1.0, 2000L));
        repository.save(buildLog("t3", "tenant-2", "openai", "gpt-4o", Scene.GENERIC, 50, 20, 0.2, 3000L));

        long count = repository.countByTenantIdAndCreatedAtBetween("tenant-1", 0L, 10000L);

        assertThat(count).isEqualTo(2L);
    }
}
