package com.agent.common.constant;

import org.junit.jupiter.api.Test;

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
}
