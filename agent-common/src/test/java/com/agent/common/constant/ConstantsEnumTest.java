package com.agent.common.constant;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConstantsEnumTest {

    @Test
    void taskStatus_hasAllTenStates() {
        assertEquals(10, TaskStatus.values().length);
        assertEquals("PENDING", TaskStatus.PENDING.name());
        assertEquals("PLANNING", TaskStatus.PLANNING.name());
        assertEquals("RUNNING", TaskStatus.RUNNING.name());
        assertEquals("SUBTASK_RUNNING", TaskStatus.SUBTASK_RUNNING.name());
        assertEquals("WAITING_HUMAN", TaskStatus.WAITING_HUMAN.name());
        assertEquals("REPLANNING", TaskStatus.REPLANNING.name());
        assertEquals("SUCCESS", TaskStatus.SUCCESS.name());
        assertEquals("FAILED", TaskStatus.FAILED.name());
        assertEquals("CANCELLED", TaskStatus.CANCELLED.name());
        assertEquals("TIMEOUT", TaskStatus.TIMEOUT.name());
    }

    @Test
    void taskStatus_isTerminal_distinguishesTerminalStates() {
        assertTrue(TaskStatus.SUCCESS.isTerminal());
        assertTrue(TaskStatus.FAILED.isTerminal());
        assertTrue(TaskStatus.CANCELLED.isTerminal());
        assertTrue(TaskStatus.TIMEOUT.isTerminal());
        assertFalse(TaskStatus.PENDING.isTerminal());
        assertFalse(TaskStatus.RUNNING.isTerminal());
        assertFalse(TaskStatus.WAITING_HUMAN.isTerminal());
    }

    @Test
    void complexityLevel_l1HasCorrectRanges() {
        assertEquals(3, ComplexityLevel.values().length);
        assertEquals(1, ComplexityLevel.L1.getLevel());
        assertEquals("L1", ComplexityLevel.L1.getCode());
        assertEquals(5, ComplexityLevel.L1.getStepRange());
        assertEquals(3, ComplexityLevel.L1.getToolRange());
        assertEquals(500L, ComplexityLevel.L1.getCostLimitCent());
    }

    @Test
    void complexityLevel_l2HasCorrectRanges() {
        assertEquals(2, ComplexityLevel.L2.getLevel());
        assertEquals(10, ComplexityLevel.L2.getStepRange());
        assertEquals(5, ComplexityLevel.L2.getToolRange());
        assertEquals(2000L, ComplexityLevel.L2.getCostLimitCent());
    }

    @Test
    void complexityLevel_l3HasCorrectRanges() {
        assertEquals(3, ComplexityLevel.L3.getLevel());
        assertEquals(30, ComplexityLevel.L3.getStepRange());
        assertEquals(10, ComplexityLevel.L3.getToolRange());
        assertEquals(10000L, ComplexityLevel.L3.getCostLimitCent());
    }

    @Test
    void complexityLevel_fromLevel_resolvesCode() {
        assertEquals(ComplexityLevel.L1, ComplexityLevel.fromLevel(1));
        assertEquals(ComplexityLevel.L2, ComplexityLevel.fromLevel(2));
        assertEquals(ComplexityLevel.L3, ComplexityLevel.fromLevel(3));
    }

    @Test
    void agentStatus_hasFourStatesWithCode() {
        assertEquals(4, AgentStatus.values().length);
        assertEquals(0, AgentStatus.DRAFT.getCode());
        assertEquals(1, AgentStatus.ONLINE.getCode());
        assertEquals(2, AgentStatus.OFFLINE.getCode());
        assertEquals(3, AgentStatus.SUSPENDED.getCode());
    }

    @Test
    void agentStatus_fromCode_resolvesEnum() {
        assertEquals(AgentStatus.DRAFT, AgentStatus.fromCode(0));
        assertEquals(AgentStatus.ONLINE, AgentStatus.fromCode(1));
        assertEquals(AgentStatus.OFFLINE, AgentStatus.fromCode(2));
        assertEquals(AgentStatus.SUSPENDED, AgentStatus.fromCode(3));
    }

    @Test
    void riskLevel_r1HasGeneralExecutor() {
        assertEquals(3, RiskLevel.values().length);
        assertEquals("R1", RiskLevel.R1.getCode());
        assertEquals(1, RiskLevel.R1.getLevel());
        assertEquals("general", RiskLevel.R1.getExecutor());
    }

    @Test
    void riskLevel_r2HasProxyExecutor() {
        assertEquals("R2", RiskLevel.R2.getCode());
        assertEquals(2, RiskLevel.R2.getLevel());
        assertEquals("proxy", RiskLevel.R2.getExecutor());
    }

    @Test
    void riskLevel_r3HasSandboxExecutor() {
        assertEquals("R3", RiskLevel.R3.getCode());
        assertEquals(3, RiskLevel.R3.getLevel());
        assertEquals("sandbox", RiskLevel.R3.getExecutor());
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
    void riskLevel_fromLevel_resolvesByLevel() {
        assertEquals(RiskLevel.R1, RiskLevel.fromLevel(1));
        assertEquals(RiskLevel.R2, RiskLevel.fromLevel(2));
        assertEquals(RiskLevel.R3, RiskLevel.fromLevel(3));
    }

    @Test
    void riskLevel_fromLevel_unknownThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> RiskLevel.fromLevel(0));
        assertThrows(IllegalArgumentException.class, () -> RiskLevel.fromLevel(4));
        assertThrows(IllegalArgumentException.class, () -> RiskLevel.fromLevel(99));
        assertThrows(IllegalArgumentException.class, () -> RiskLevel.fromLevel(-1));
    }

    /**
     * P3-6 整改：补 TaskStatus.getLegalNextStatuses 方法覆盖率（P3-1 新增方法）。
     *
     * <p>P3-1 在 TaskStatus 枚举中新增 {@code getLegalNextStatuses()} 方法（基于 LEGAL_NEXT_STATUSES 静态矩阵），
     * 但 ConstantsEnumTest 未调用该方法，导致 method_missed=1、line_missed=1。
     * 本测试覆盖全部 10 状态的合法后继集合，验证流转矩阵正确性。</p>
     */
    @Test
    void taskStatus_getLegalNextStatuses_returnsCorrectSuccessors() {
        // 非终态：每个状态都有合法后继
        assertEquals(
                Set.of(TaskStatus.PLANNING, TaskStatus.RUNNING, TaskStatus.FAILED,
                        TaskStatus.CANCELLED, TaskStatus.TIMEOUT),
                TaskStatus.PENDING.getLegalNextStatuses());
        assertEquals(
                Set.of(TaskStatus.RUNNING, TaskStatus.WAITING_HUMAN, TaskStatus.FAILED,
                        TaskStatus.CANCELLED, TaskStatus.TIMEOUT),
                TaskStatus.PLANNING.getLegalNextStatuses());
        assertEquals(
                Set.of(TaskStatus.SUBTASK_RUNNING, TaskStatus.WAITING_HUMAN,
                        TaskStatus.REPLANNING, TaskStatus.FAILED, TaskStatus.CANCELLED,
                        TaskStatus.TIMEOUT),
                TaskStatus.RUNNING.getLegalNextStatuses());
        assertEquals(
                Set.of(TaskStatus.WAITING_HUMAN, TaskStatus.REPLANNING,
                        TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.CANCELLED,
                        TaskStatus.TIMEOUT),
                TaskStatus.SUBTASK_RUNNING.getLegalNextStatuses());
        assertEquals(
                Set.of(TaskStatus.RUNNING, TaskStatus.REPLANNING, TaskStatus.SUCCESS,
                        TaskStatus.FAILED, TaskStatus.CANCELLED),
                TaskStatus.WAITING_HUMAN.getLegalNextStatuses());
        assertEquals(
                Set.of(TaskStatus.RUNNING, TaskStatus.SUBTASK_RUNNING,
                        TaskStatus.WAITING_HUMAN, TaskStatus.FAILED, TaskStatus.CANCELLED,
                        TaskStatus.TIMEOUT),
                TaskStatus.REPLANNING.getLegalNextStatuses());
        // 终态：SUCCESS / CANCELLED 返回空集合
        assertTrue(TaskStatus.SUCCESS.getLegalNextStatuses().isEmpty());
        assertTrue(TaskStatus.CANCELLED.getLegalNextStatuses().isEmpty());
        // 终态但有特殊恢复路径：FAILED / TIMEOUT 可回 WAITING_HUMAN
        assertEquals(Set.of(TaskStatus.WAITING_HUMAN),
                TaskStatus.FAILED.getLegalNextStatuses());
        assertEquals(Set.of(TaskStatus.WAITING_HUMAN),
                TaskStatus.TIMEOUT.getLegalNextStatuses());
    }

    @Test
    void taskStatus_getLegalNextStatuses_isImmutable() {
        // 返回的 Set 不可被外部修改
        Set<TaskStatus> successors = TaskStatus.PENDING.getLegalNextStatuses();
        assertThrows(UnsupportedOperationException.class,
                () -> successors.add(TaskStatus.SUCCESS));
    }

    /**
     * P3-6 整改：补 ComplexityLevel.fromLevel 异常分支覆盖率。
     *
     * <p>原测试仅覆盖 {@code complexityLevel_fromLevel_resolvesCode} 的正常路径（1/2/3），
     * fromLevel 方法的异常分支（level 不匹配抛 IllegalArgumentException）未被覆盖，
     * branch_missed=1。本测试覆盖异常分支。</p>
     */
    @Test
    void complexityLevel_fromLevel_unknownThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ComplexityLevel.fromLevel(0));
        assertThrows(IllegalArgumentException.class, () -> ComplexityLevel.fromLevel(4));
        assertThrows(IllegalArgumentException.class, () -> ComplexityLevel.fromLevel(99));
    }

    /**
     * P3-6 整改：补 AgentStatus.fromCode 异常分支覆盖率。
     *
     * <p>原测试仅覆盖 {@code agentStatus_fromCode_resolvesEnum} 的正常路径（0/1/2/3），
     * fromCode 方法的异常分支（code 不匹配抛 IllegalArgumentException）未被覆盖，
     * branch_missed=1。本测试覆盖异常分支。</p>
     */
    @Test
    void agentStatus_fromCode_unknownThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> AgentStatus.fromCode(4));
        assertThrows(IllegalArgumentException.class, () -> AgentStatus.fromCode(5));
        assertThrows(IllegalArgumentException.class, () -> AgentStatus.fromCode(-1));
        assertThrows(IllegalArgumentException.class, () -> AgentStatus.fromCode(99));
    }
}
