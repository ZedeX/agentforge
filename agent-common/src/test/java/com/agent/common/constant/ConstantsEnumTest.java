package com.agent.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstantsEnumTest {

    @Test
    @DisplayName("TaskStatus 应包含全部 10 种状态")
    void should_HaveAllTenStates_When_TaskStatusEnumerated() {
        assertThat(TaskStatus.values().length).isEqualTo(10);
        assertThat(TaskStatus.PENDING.name()).isEqualTo("PENDING");
        assertThat(TaskStatus.PLANNING.name()).isEqualTo("PLANNING");
        assertThat(TaskStatus.RUNNING.name()).isEqualTo("RUNNING");
        assertThat(TaskStatus.SUBTASK_RUNNING.name()).isEqualTo("SUBTASK_RUNNING");
        assertThat(TaskStatus.WAITING_HUMAN.name()).isEqualTo("WAITING_HUMAN");
        assertThat(TaskStatus.REPLANNING.name()).isEqualTo("REPLANNING");
        assertThat(TaskStatus.SUCCESS.name()).isEqualTo("SUCCESS");
        assertThat(TaskStatus.FAILED.name()).isEqualTo("FAILED");
        assertThat(TaskStatus.CANCELLED.name()).isEqualTo("CANCELLED");
        assertThat(TaskStatus.TIMEOUT.name()).isEqualTo("TIMEOUT");
    }

    @Test
    @DisplayName("TaskStatus.isTerminal 应区分终态与非终态")
    void should_DistinguishTerminalStates_When_TaskStatusIsTerminalInvoked() {
        assertThat(TaskStatus.SUCCESS.isTerminal()).isTrue();
        assertThat(TaskStatus.FAILED.isTerminal()).isTrue();
        assertThat(TaskStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(TaskStatus.TIMEOUT.isTerminal()).isTrue();
        assertThat(TaskStatus.PENDING.isTerminal()).isFalse();
        assertThat(TaskStatus.RUNNING.isTerminal()).isFalse();
        assertThat(TaskStatus.WAITING_HUMAN.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("ComplexityLevel L1 应具有正确的范围配置")
    void should_HaveCorrectRanges_When_ComplexityLevelIsL1() {
        assertThat(ComplexityLevel.values().length).isEqualTo(3);
        assertThat(ComplexityLevel.L1.getLevel()).isEqualTo(1);
        assertThat(ComplexityLevel.L1.getCode()).isEqualTo("L1");
        assertThat(ComplexityLevel.L1.getStepRange()).isEqualTo(5);
        assertThat(ComplexityLevel.L1.getToolRange()).isEqualTo(3);
        assertThat(ComplexityLevel.L1.getCostLimitCent()).isEqualTo(500L);
    }

    @Test
    @DisplayName("ComplexityLevel L2 应具有正确的范围配置")
    void should_HaveCorrectRanges_When_ComplexityLevelIsL2() {
        assertThat(ComplexityLevel.L2.getLevel()).isEqualTo(2);
        assertThat(ComplexityLevel.L2.getStepRange()).isEqualTo(10);
        assertThat(ComplexityLevel.L2.getToolRange()).isEqualTo(5);
        assertThat(ComplexityLevel.L2.getCostLimitCent()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("ComplexityLevel L3 应具有正确的范围配置")
    void should_HaveCorrectRanges_When_ComplexityLevelIsL3() {
        assertThat(ComplexityLevel.L3.getLevel()).isEqualTo(3);
        assertThat(ComplexityLevel.L3.getStepRange()).isEqualTo(30);
        assertThat(ComplexityLevel.L3.getToolRange()).isEqualTo(10);
        assertThat(ComplexityLevel.L3.getCostLimitCent()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("ComplexityLevel.fromLevel 应按 level 解析对应枚举")
    void should_ResolveCode_When_ComplexityLevelFromLevelInvoked() {
        assertThat(ComplexityLevel.fromLevel(1)).isEqualTo(ComplexityLevel.L1);
        assertThat(ComplexityLevel.fromLevel(2)).isEqualTo(ComplexityLevel.L2);
        assertThat(ComplexityLevel.fromLevel(3)).isEqualTo(ComplexityLevel.L3);
    }

    @Test
    @DisplayName("AgentStatus 应包含 4 种带 code 的状态")
    void should_HaveFourStatesWithCode_When_AgentStatusEnumerated() {
        assertThat(AgentStatus.values().length).isEqualTo(4);
        assertThat(AgentStatus.DRAFT.getCode()).isEqualTo(0);
        assertThat(AgentStatus.ONLINE.getCode()).isEqualTo(1);
        assertThat(AgentStatus.OFFLINE.getCode()).isEqualTo(2);
        assertThat(AgentStatus.SUSPENDED.getCode()).isEqualTo(3);
    }

    @Test
    @DisplayName("AgentStatus.fromCode 应按 code 解析对应枚举")
    void should_ResolveEnum_When_AgentStatusFromCodeInvoked() {
        assertThat(AgentStatus.fromCode(0)).isEqualTo(AgentStatus.DRAFT);
        assertThat(AgentStatus.fromCode(1)).isEqualTo(AgentStatus.ONLINE);
        assertThat(AgentStatus.fromCode(2)).isEqualTo(AgentStatus.OFFLINE);
        assertThat(AgentStatus.fromCode(3)).isEqualTo(AgentStatus.SUSPENDED);
    }

    @Test
    @DisplayName("RiskLevel R1 应使用 general 执行器")
    void should_HaveGeneralExecutor_When_RiskLevelIsR1() {
        assertThat(RiskLevel.values().length).isEqualTo(3);
        assertThat(RiskLevel.R1.getCode()).isEqualTo("R1");
        assertThat(RiskLevel.R1.getLevel()).isEqualTo(1);
        assertThat(RiskLevel.R1.getExecutor()).isEqualTo("general");
    }

    @Test
    @DisplayName("RiskLevel R2 应使用 proxy 执行器")
    void should_HaveProxyExecutor_When_RiskLevelIsR2() {
        assertThat(RiskLevel.R2.getCode()).isEqualTo("R2");
        assertThat(RiskLevel.R2.getLevel()).isEqualTo(2);
        assertThat(RiskLevel.R2.getExecutor()).isEqualTo("proxy");
    }

    @Test
    @DisplayName("RiskLevel R3 应使用 sandbox 执行器")
    void should_HaveSandboxExecutor_When_RiskLevelIsR3() {
        assertThat(RiskLevel.R3.getCode()).isEqualTo("R3");
        assertThat(RiskLevel.R3.getLevel()).isEqualTo(3);
        assertThat(RiskLevel.R3.getExecutor()).isEqualTo("sandbox");
    }

    /**
     * P3-6 整改：补 RiskLevel.fromLevel 异常分支覆盖率。
     *
     * <p>{@link RiskLevel#fromLevel(int)} 在 level 不匹配任何枚举常量时抛 IllegalArgumentException。
     * 原测试仅覆盖 {@code riskLevel_r1/r2/r3HasXxxExecutor} 的常量字段读取，
     * 未调用 fromLevel 方法，导致该方法 4 个 branch 全部未覆盖（branch 0/4 = 0%）。
     * 本测试同时覆盖正常解析（1/2/3）与异常分支（0/4/99）。</p>
     */
    @Test
    @DisplayName("RiskLevel.fromLevel 应按 level 解析对应枚举")
    void should_ResolveByLevel_When_RiskLevelFromLevelInvoked() {
        assertThat(RiskLevel.fromLevel(1)).isEqualTo(RiskLevel.R1);
        assertThat(RiskLevel.fromLevel(2)).isEqualTo(RiskLevel.R2);
        assertThat(RiskLevel.fromLevel(3)).isEqualTo(RiskLevel.R3);
    }

    @Test
    @DisplayName("RiskLevel.fromLevel 对未知 level 应抛 IllegalArgumentException")
    void should_ThrowIllegalArgumentException_When_RiskLevelFromLevelReceivesUnknown() {
        assertThatThrownBy(() -> RiskLevel.fromLevel(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RiskLevel.fromLevel(4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RiskLevel.fromLevel(99)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RiskLevel.fromLevel(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * P3-6 整改：补 TaskStatus.getLegalNextStatuses 方法覆盖率（P3-1 新增方法）。
     *
     * <p>P3-1 在 TaskStatus 枚举中新增 {@code getLegalNextStatuses()} 方法（基于 LEGAL_NEXT_STATUSES 静态矩阵），
     * 但 ConstantsEnumTest 未调用该方法，导致 method_missed=1、line_missed=1。
     * 本测试覆盖全部 10 状态的合法后继集合，验证流转矩阵正确性。</p>
     */
    @Test
    @DisplayName("TaskStatus.getLegalNextStatuses 应返回正确的合法后继状态集合")
    void should_ReturnCorrectSuccessors_When_GetLegalNextStatusesInvoked() {
        // 非终态：每个状态都有合法后继
        assertThat(TaskStatus.PENDING.getLegalNextStatuses())
                .isEqualTo(Set.of(TaskStatus.PLANNING, TaskStatus.RUNNING, TaskStatus.FAILED,
                        TaskStatus.CANCELLED, TaskStatus.TIMEOUT));
        assertThat(TaskStatus.PLANNING.getLegalNextStatuses())
                .isEqualTo(Set.of(TaskStatus.RUNNING, TaskStatus.WAITING_HUMAN, TaskStatus.FAILED,
                        TaskStatus.CANCELLED, TaskStatus.TIMEOUT));
        assertThat(TaskStatus.RUNNING.getLegalNextStatuses())
                .isEqualTo(Set.of(TaskStatus.SUBTASK_RUNNING, TaskStatus.WAITING_HUMAN,
                        TaskStatus.REPLANNING, TaskStatus.FAILED, TaskStatus.CANCELLED,
                        TaskStatus.TIMEOUT));
        assertThat(TaskStatus.SUBTASK_RUNNING.getLegalNextStatuses())
                .isEqualTo(Set.of(TaskStatus.WAITING_HUMAN, TaskStatus.REPLANNING,
                        TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.CANCELLED,
                        TaskStatus.TIMEOUT));
        assertThat(TaskStatus.WAITING_HUMAN.getLegalNextStatuses())
                .isEqualTo(Set.of(TaskStatus.RUNNING, TaskStatus.REPLANNING, TaskStatus.SUCCESS,
                        TaskStatus.FAILED, TaskStatus.CANCELLED));
        assertThat(TaskStatus.REPLANNING.getLegalNextStatuses())
                .isEqualTo(Set.of(TaskStatus.RUNNING, TaskStatus.SUBTASK_RUNNING,
                        TaskStatus.WAITING_HUMAN, TaskStatus.FAILED, TaskStatus.CANCELLED,
                        TaskStatus.TIMEOUT));
        // 终态：SUCCESS / CANCELLED 返回空集合
        assertThat(TaskStatus.SUCCESS.getLegalNextStatuses()).isEmpty();
        assertThat(TaskStatus.CANCELLED.getLegalNextStatuses()).isEmpty();
        // 终态但有特殊恢复路径：FAILED / TIMEOUT 可回 WAITING_HUMAN
        assertThat(TaskStatus.FAILED.getLegalNextStatuses())
                .isEqualTo(Set.of(TaskStatus.WAITING_HUMAN));
        assertThat(TaskStatus.TIMEOUT.getLegalNextStatuses())
                .isEqualTo(Set.of(TaskStatus.WAITING_HUMAN));
    }

    @Test
    @DisplayName("TaskStatus.getLegalNextStatuses 返回的集合应不可变")
    void should_BeImmutable_When_GetLegalNextStatusesReturnsSet() {
        // 返回的 Set 不可被外部修改
        Set<TaskStatus> successors = TaskStatus.PENDING.getLegalNextStatuses();
        assertThatThrownBy(() -> successors.add(TaskStatus.SUCCESS))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * P3-6 整改：补 ComplexityLevel.fromLevel 异常分支覆盖率。
     *
     * <p>原测试仅覆盖 {@code complexityLevel_fromLevel_resolvesCode} 的正常路径（1/2/3），
     * fromLevel 方法的异常分支（level 不匹配抛 IllegalArgumentException）未被覆盖，
     * branch_missed=1。本测试覆盖异常分支。</p>
     */
    @Test
    @DisplayName("ComplexityLevel.fromLevel 对未知 level 应抛 IllegalArgumentException")
    void should_ThrowIllegalArgumentException_When_ComplexityLevelFromLevelReceivesUnknown() {
        assertThatThrownBy(() -> ComplexityLevel.fromLevel(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplexityLevel.fromLevel(4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplexityLevel.fromLevel(99)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * P3-6 整改：补 AgentStatus.fromCode 异常分支覆盖率。
     *
     * <p>原测试仅覆盖 {@code agentStatus_fromCode_resolvesEnum} 的正常路径（0/1/2/3），
     * fromCode 方法的异常分支（code 不匹配抛 IllegalArgumentException）未被覆盖，
     * branch_missed=1。本测试覆盖异常分支。</p>
     */
    @Test
    @DisplayName("AgentStatus.fromCode 对未知 code 应抛 IllegalArgumentException")
    void should_ThrowIllegalArgumentException_When_AgentStatusFromCodeReceivesUnknown() {
        assertThatThrownBy(() -> AgentStatus.fromCode(4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentStatus.fromCode(5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentStatus.fromCode(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentStatus.fromCode(99)).isInstanceOf(IllegalArgumentException.class);
    }
}
