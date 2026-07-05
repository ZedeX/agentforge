package com.agent.runtime.api.impl;

import com.agent.runtime.enums.SessionStatus;
import com.agent.runtime.model.AgentSession;
import com.agent.runtime.repository.AgentSessionRepository;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Agent session lifecycle manager (T7, doc 06-runtime §7.3 / §9.1).
 *
 * <p>Wraps {@link AgentSessionRepository} to provide session create / get / pause / resume / finish
 * operations with status transition validation and audit timestamps.
 *
 * <p>Used by {@code AgentRuntimeGrpcImpl} (T10) to back the 5 RPCs:
 * <ul>
 *   <li>StartAgent → {@link #startSession}</li>
 *   <li>Step → {@link #getSession} (read-only)</li>
 *   <li>GetState → {@link #getSession}</li>
 *   <li>Pause → {@link #pauseSession}</li>
 *   <li>Resume → {@link #resumeSession}</li>
 * </ul>
 */
@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final AgentSessionRepository sessionRepository;

    public SessionManager(AgentSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /** Create and persist a new AgentSession with status=CREATING (then transition to RUNNING). */
    @Transactional
    public AgentSession startSession(long agentId, int agentVersion, String taskId,
                                     String subtaskId, String nodeId, String inputsJson,
                                     int maxSteps, int tokenBudget, long costBudgetCent) {
        AgentSession session = new AgentSession();
        session.setAgentId(agentId);
        session.setAgentVersion(agentVersion);
        session.setTaskId(taskId);
        session.setSubtaskId(subtaskId);
        session.setNodeId(nodeId);
        session.setInputsJson(inputsJson);
        session.setMaxSteps(maxSteps);
        session.setTokenBudget(tokenBudget);
        session.setCostBudgetCent(costBudgetCent);
        session.setCostUsedCent(0L);
        session.setCurrentStepNumber(0);
        session.setTokenUsed(0);
        session.setStatus(SessionStatus.RUNNING);
        session.setStartedAt(Instant.now());

        AgentSession saved = sessionRepository.save(session);
        log.info("Session started: agentInstanceId={}, agentId={}, taskId={}",
                saved.getAgentInstanceId(), agentId, taskId);
        return saved;
    }

    /** Get session by agentInstanceId. Throws SESSION_NOT_FOUND if missing. */
    @Transactional(readOnly = true)
    public AgentSession getSession(String agentInstanceId) {
        return sessionRepository.findByAgentInstanceId(agentInstanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND,
                        "session not found: agentInstanceId=" + agentInstanceId));
    }

    /** Get session optionally (no throw). */
    @Transactional(readOnly = true)
    public Optional<AgentSession> findSession(String agentInstanceId) {
        return sessionRepository.findByAgentInstanceId(agentInstanceId);
    }

    /** Pause a running session. Returns updated session. */
    @Transactional
    public AgentSession pauseSession(String agentInstanceId, String reason) {
        AgentSession session = getSession(agentInstanceId);
        if (session.getStatus() != SessionStatus.RUNNING) {
            throw new BusinessException(ErrorCode.AGENT_STATUS_CONFLICT,
                    "cannot pause session in status=" + session.getStatus()
                            + ", agentInstanceId=" + agentInstanceId);
        }
        session.setStatus(SessionStatus.PAUSED);
        session.setPausedAt(Instant.now());
        if (reason != null && !reason.isEmpty()) {
            session.setErrorMessage(reason);
        }
        AgentSession saved = sessionRepository.save(session);
        log.info("Session paused: agentInstanceId={}, reason={}", agentInstanceId, reason);
        return saved;
    }

    /** Resume a paused session. Returns updated session. */
    @Transactional
    public AgentSession resumeSession(String agentInstanceId) {
        AgentSession session = getSession(agentInstanceId);
        if (session.getStatus() != SessionStatus.PAUSED) {
            throw new BusinessException(ErrorCode.AGENT_STATUS_CONFLICT,
                    "cannot resume session in status=" + session.getStatus()
                            + ", agentInstanceId=" + agentInstanceId);
        }
        session.setStatus(SessionStatus.RUNNING);
        session.setResumedAt(Instant.now());
        AgentSession saved = sessionRepository.save(session);
        log.info("Session resumed: agentInstanceId={}", agentInstanceId);
        return saved;
    }

    /** Finish a session with terminal status (SUCCESS / FAILED / CANCELLED). */
    @Transactional
    public AgentSession finishSession(String agentInstanceId, SessionStatus terminalStatus,
                                      String errorMessage) {
        AgentSession session = getSession(agentInstanceId);
        session.setStatus(terminalStatus);
        session.setEndedAt(Instant.now());
        if (errorMessage != null) {
            session.setErrorMessage(errorMessage);
        }
        AgentSession saved = sessionRepository.save(session);
        log.info("Session finished: agentInstanceId={}, status={}", agentInstanceId, terminalStatus);
        return saved;
    }

    /** Update token / cost usage counters after a step. */
    @Transactional
    public AgentSession updateUsage(String agentInstanceId, int tokenUsedDelta, long costUsedDeltaCent) {
        AgentSession session = getSession(agentInstanceId);
        session.setTokenUsed(session.getTokenUsed() + tokenUsedDelta);
        session.setCostUsedCent(session.getCostUsedCent() + costUsedDeltaCent);
        session.setCurrentStepNumber(session.getCurrentStepNumber() + 1);
        return sessionRepository.save(session);
    }
}
