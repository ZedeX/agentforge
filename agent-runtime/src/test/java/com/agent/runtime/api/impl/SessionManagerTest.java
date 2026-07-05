package com.agent.runtime.api.impl;

import com.agent.common.exception.BusinessException;
import com.agent.runtime.enums.SessionStatus;
import com.agent.runtime.model.AgentSession;
import com.agent.runtime.repository.AgentSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link SessionManager} 单元测试 (T7, doc 06-runtime §7.3 / §9.1).
 *
 * <p>验证会话生命周期管理:
 * <ul>
 *   <li>startSession: 创建并持久化 AgentSession (status=RUNNING)</li>
 *   <li>getSession: 按 agentInstanceId 查询; 不存在抛 AGENT_NOT_FOUND</li>
 *   <li>pauseSession: RUNNING → PAUSED + pausedAt; 非 RUNNING 抛 AGENT_STATUS_CONFLICT</li>
 *   <li>resumeSession: PAUSED → RUNNING + resumedAt; 非 PAUSED 抛 AGENT_STATUS_CONFLICT</li>
 *   <li>finishSession: 任意状态 → terminal + endedAt</li>
 *   <li>updateUsage: token/cost 计数累加 + stepNumber++</li>
 * </ul>
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(SessionManager.class)
class SessionManagerTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AgentSessionRepository repository;

    @Autowired
    private SessionManager sessionManager;

    @Test
    @DisplayName("startSession: 应创建 AgentSession 并持久化, status=RUNNING")
    void startSession_should_createAndPersist() {
        AgentSession session = sessionManager.startSession(
                1001L, 1, "task_001", "sub_001", "node_001",
                "{\"input\":\"x\"}", 20, 32000, 1000L);

        assertThat(session)
                .as("返回非 null session")
                .isNotNull();
        assertThat(session.getAgentInstanceId())
                .as("应自动生成 agentInstanceId")
                .isNotBlank();
        assertThat(session.getStatus())
                .as("初始 status 应为 RUNNING")
                .isEqualTo(SessionStatus.RUNNING);
        assertThat(session.getStartedAt())
                .as("startedAt 应非 null")
                .isNotNull();

        entityManager.flush();
        entityManager.clear();

        Optional<AgentSession> found = repository.findByAgentInstanceId(session.getAgentInstanceId());
        assertThat(found).isPresent();
        assertThat(found.get().getAgentId()).isEqualTo(1001L);
        assertThat(found.get().getTaskId()).isEqualTo("task_001");
    }

    @Test
    @DisplayName("getSession: 按 agentInstanceId 查询; 不存在应抛 BusinessException")
    void getSession_should_throw_when_notFound() {
        BusinessException ex = catchThrowableOfType(
                () -> sessionManager.getSession("nonexistent_id"),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode().name()).isEqualTo("AGENT_NOT_FOUND");
    }

    @Test
    @DisplayName("pauseSession: RUNNING → PAUSED + pausedAt")
    void pauseSession_should_transitionToPaused() {
        AgentSession session = sessionManager.startSession(
                2002L, 1, "task_002", null, null, "{}", 20, 32000, 0L);

        AgentSession paused = sessionManager.pauseSession(session.getAgentInstanceId(), "manual pause");

        assertThat(paused.getStatus()).isEqualTo(SessionStatus.PAUSED);
        assertThat(paused.getPausedAt()).isNotNull();
        assertThat(paused.getErrorMessage()).isEqualTo("manual pause");
    }

    @Test
    @DisplayName("pauseSession: 非 RUNNING 状态应抛 AGENT_STATUS_CONFLICT")
    void pauseSession_should_throw_when_notRunning() {
        AgentSession session = sessionManager.startSession(
                3003L, 1, "task_003", null, null, "{}", 20, 32000, 0L);
        sessionManager.pauseSession(session.getAgentInstanceId(), null);

        BusinessException ex = catchThrowableOfType(
                () -> sessionManager.pauseSession(session.getAgentInstanceId(), "double pause"),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode().name()).isEqualTo("AGENT_STATUS_CONFLICT");
    }

    @Test
    @DisplayName("resumeSession: PAUSED → RUNNING + resumedAt")
    void resumeSession_should_transitionToRunning() {
        AgentSession session = sessionManager.startSession(
                4004L, 1, "task_004", null, null, "{}", 20, 32000, 0L);
        sessionManager.pauseSession(session.getAgentInstanceId(), null);

        AgentSession resumed = sessionManager.resumeSession(session.getAgentInstanceId());

        assertThat(resumed.getStatus()).isEqualTo(SessionStatus.RUNNING);
        assertThat(resumed.getResumedAt()).isNotNull();
    }

    @Test
    @DisplayName("resumeSession: 非 PAUSED 状态应抛 AGENT_STATUS_CONFLICT")
    void resumeSession_should_throw_when_notPaused() {
        AgentSession session = sessionManager.startSession(
                5005L, 1, "task_005", null, null, "{}", 20, 32000, 0L);

        BusinessException ex = catchThrowableOfType(
                () -> sessionManager.resumeSession(session.getAgentInstanceId()),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode().name()).isEqualTo("AGENT_STATUS_CONFLICT");
    }

    @Test
    @DisplayName("finishSession: 应设置 terminal status + endedAt + errorMessage")
    void finishSession_should_setTerminalStatus() {
        AgentSession session = sessionManager.startSession(
                6006L, 1, "task_006", null, null, "{}", 20, 32000, 0L);

        AgentSession finished = sessionManager.finishSession(
                session.getAgentInstanceId(), SessionStatus.SUCCESS, null);

        assertThat(finished.getStatus()).isEqualTo(SessionStatus.SUCCESS);
        assertThat(finished.getEndedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateUsage: 应累加 token/cost 计数并递增 stepNumber")
    void updateUsage_should_accumulateTokens() {
        AgentSession session = sessionManager.startSession(
                7007L, 1, "task_007", null, null, "{}", 20, 32000, 0L);

        sessionManager.updateUsage(session.getAgentInstanceId(), 500, 10L);
        AgentSession updated = sessionManager.updateUsage(session.getAgentInstanceId(), 300, 5L);

        assertThat(updated.getTokenUsed()).isEqualTo(800);
        assertThat(updated.getCostUsedCent()).isEqualTo(15L);
        assertThat(updated.getCurrentStepNumber()).isEqualTo(2);
    }
}
