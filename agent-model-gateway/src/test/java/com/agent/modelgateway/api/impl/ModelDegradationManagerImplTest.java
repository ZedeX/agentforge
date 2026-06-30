package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.enums.ProviderStatus;
import com.agent.modelgateway.model.ProviderHealth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ModelDegradationManagerImpl unit tests (doc 02-api §7).
 */
@DisplayName("ModelDegradationManagerImpl 故障降级管理器")
class ModelDegradationManagerImplTest {

    private final ModelDegradationManagerImpl manager = new ModelDegradationManagerImpl();

    @Test
    @DisplayName("初始状态为 ACTIVE")
    void should_ReturnActive_When_NoFailuresRecorded() {
        assertThat(manager.getStatus("openai")).isEqualTo(ProviderStatus.ACTIVE);
    }

    @Test
    @DisplayName("连续失败 < 3 次保持 ACTIVE")
    void should_StayActive_When_FailuresBelowThreshold() {
        manager.recordFailure("openai");
        manager.recordFailure("openai");
        assertThat(manager.getStatus("openai")).isEqualTo(ProviderStatus.ACTIVE);
        assertThat(manager.getHealth("openai").getConsecutiveFailures()).isEqualTo(2);
    }

    @Test
    @DisplayName("连续失败 ≥ 3 次切换到 DEGRADED")
    void should_Degrade_When_ConsecutiveFailuresReachThreshold() {
        manager.recordFailure("openai");
        manager.recordFailure("openai");
        manager.recordFailure("openai");
        assertThat(manager.getStatus("openai")).isEqualTo(ProviderStatus.DEGRADED);
        assertThat(manager.getHealth("openai").getDegradedAt()).isPositive();
    }

    @Test
    @DisplayName("成功调用重置连续失败计数")
    void should_ResetFailureCount_When_SuccessRecorded() {
        manager.recordFailure("openai");
        manager.recordFailure("openai");
        manager.recordSuccess("openai");
        assertThat(manager.getStatus("openai")).isEqualTo(ProviderStatus.ACTIVE);
        assertThat(manager.getHealth("openai").getConsecutiveFailures()).isZero();
    }

    @Test
    @DisplayName("RECOVERING 状态下成功调用恢复为 ACTIVE")
    void should_RecoverToActive_When_SuccessDuringRecovering() {
        // Force degrade
        for (int i = 0; i < 3; i++) manager.recordFailure("openai");
        assertThat(manager.getStatus("openai")).isEqualTo(ProviderStatus.DEGRADED);

        // Manually set to RECOVERING (simulate cooldown elapsed)
        manager.getHealth("openai").setStatus(ProviderStatus.RECOVERING);

        manager.recordSuccess("openai");
        assertThat(manager.getStatus("openai")).isEqualTo(ProviderStatus.ACTIVE);
    }

    @Test
    @DisplayName("getHealth 返回完整健康快照")
    void should_ReturnFullHealthSnapshot_When_GetHealthCalled() {
        manager.recordSuccess("openai");
        manager.recordFailure("openai");
        ProviderHealth health = manager.getHealth("openai");
        assertThat(health.getProviderCode()).isEqualTo("openai");
        assertThat(health.getTotalRequests()).isEqualTo(2);
        assertThat(health.getSuccessCount()).isEqualTo(1);
        assertThat(health.getConsecutiveFailures()).isEqualTo(1);
        assertThat(health.getSuccessRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("tickRecovery 冷却期未到保持 DEGRADED")
    void should_StayDegraded_When_CooldownNotElapsed() {
        for (int i = 0; i < 3; i++) manager.recordFailure("openai");
        assertThat(manager.getStatus("openai")).isEqualTo(ProviderStatus.DEGRADED);
        manager.tickRecovery("openai");
        assertThat(manager.getStatus("openai")).isEqualTo(ProviderStatus.DEGRADED);
    }

    @Test
    @DisplayName("tickRecovery 冷却期到后切换到 RECOVERING")
    void should_TransitionToRecovering_When_CooldownElapsed() {
        for (int i = 0; i < 3; i++) manager.recordFailure("openai");
        assertThat(manager.getStatus("openai")).isEqualTo(ProviderStatus.DEGRADED);
        // Manually set degradedAt to past (6 min ago, > 5min cooldown)
        manager.getHealth("openai").setDegradedAt(System.currentTimeMillis() - 6 * 60 * 1000);
        manager.tickRecovery("openai");
        assertThat(manager.getStatus("openai")).isEqualTo(ProviderStatus.RECOVERING);
    }

    @Test
    @DisplayName("tickRecovery 对 ACTIVE 状态无影响")
    void should_NoOp_When_TickRecoveryOnActive() {
        manager.tickRecovery("fresh-provider");
        assertThat(manager.getStatus("fresh-provider")).isEqualTo(ProviderStatus.ACTIVE);
    }

    @Test
    @DisplayName("null providerCode 安全兜底为 unknown")
    void should_HandleNullProviderCode_When_RecordCalled() {
        manager.recordSuccess(null);
        manager.recordFailure(null);
        // should not throw; health tracked under "unknown"
        assertThat(manager.getHealth(null).getProviderCode()).isEqualTo("unknown");
    }
}
