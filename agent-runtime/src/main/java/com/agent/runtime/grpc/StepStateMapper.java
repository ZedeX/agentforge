package com.agent.runtime.grpc;

import com.agent.runtime.enums.ReActPhaseType;
import com.agent.runtime.enums.SessionStatus;
import com.agent.runtime.enums.StepStatus;
import com.agent.runtime.model.AgentSession;
import com.agent.runtime.model.StepState;
import agentplatform.agent_runtime.v1.AgentState;
import agentplatform.agent_runtime.v1.StartAgentRequest;
import agentplatform.agent_runtime.v1.StartAgentResponse;
import agentplatform.agent_runtime.v1.PauseResponse;
import agentplatform.agent_runtime.v1.ResumeResponse;
import agentplatform.agent_runtime.v1.StepResponse;

import java.time.Instant;

/**
 * Proto ↔ JPA entity mapper for AgentRuntime gRPC service (T10, doc 06 §7).
 *
 * <p>All proto construction is centralized here so that
 * {@link AgentRuntimeGrpcImpl} only does unmarshal→delegate→marshal.
 */
public class StepStateMapper {

    private StepStateMapper() {}

    // ============ Request → Domain (no-op, use proto fields directly) ============

    // ============ Domain → Proto ============

    /** Map {@link AgentSession} → {@link StartAgentResponse}. */
    public static StartAgentResponse toStartAgentResponse(AgentSession session) {
        return StartAgentResponse.newBuilder()
                .setAgentInstanceId(session.getAgentInstanceId())
                .setStatus(session.getStatus().name())
                .setStartedAt(toEpochMilli(session.getStartedAt()))
                .build();
    }

    /** Map {@link StepState} → {@link StepResponse}. */
    public static StepResponse toStepResponse(StepState step) {
        StepResponse.Builder builder = StepResponse.newBuilder()
                .setAgentInstanceId(step.getAgentInstanceId())
                .setStepNo(step.getStepNo())
                .setPhase(mapPhase(step.getPhase()))
                .setStatus(mapStepStatus(step.getStatus()))
                .setFinished(isTerminal(step.getStatus()));

        if (step.getActionType() != null) builder.setActionType(step.getActionType());
        if (step.getActionTarget() != null) builder.setActionTarget(step.getActionTarget());
        if (step.getInputJson() != null) builder.setInputJson(step.getInputJson());
        if (step.getOutputJson() != null) builder.setOutputJson(step.getOutputJson());
        if (step.getTokensUsed() != null) builder.setTokenUsed(step.getTokensUsed());
        if (step.getCostCent() != null) builder.setCostCent(step.getCostCent());
        if (step.getDurationMs() != null) builder.setDurationMs(step.getDurationMs().intValue());

        return builder.build();
    }

    /** Map {@link AgentSession} → {@link AgentState}. */
    public static AgentState toAgentState(AgentSession session) {
        AgentState.Builder builder = AgentState.newBuilder()
                .setAgentInstanceId(session.getAgentInstanceId())
                .setMaxSteps(session.getMaxSteps())
                .setTokenUsed(session.getTokenUsed())
                .setTokenBudget(session.getTokenBudget())
                .setCostUsedCent(session.getCostUsedCent())
                .setCostBudgetCent(session.getCostBudgetCent())
                .setStatus(mapSessionStatus(session.getStatus()));

        if (session.getTaskId() != null) builder.setTaskId(session.getTaskId());
        if (session.getSubtaskId() != null) builder.setSubtaskId(session.getSubtaskId());
        if (session.getNodeId() != null) builder.setNodeId(session.getNodeId());
        if (session.getCurrentStepNumber() != null) builder.setCurrentStep(session.getCurrentStepNumber());
        if (session.getStartedAt() != null) builder.setStartedAt(toEpochMilli(session.getStartedAt()));
        if (session.getUpdatedAt() != null) builder.setUpdatedAt(toEpochMilli(session.getUpdatedAt()));

        return builder.build();
    }

    /** Map {@link AgentSession} → {@link PauseResponse}. */
    public static PauseResponse toPauseResponse(AgentSession session) {
        return PauseResponse.newBuilder()
                .setAgentInstanceId(session.getAgentInstanceId())
                .setStatus(session.getStatus().name())
                .setPausedAt(toEpochMilli(session.getPausedAt()))
                .build();
    }

    /** Map {@link AgentSession} → {@link ResumeResponse}. */
    public static ResumeResponse toResumeResponse(AgentSession session) {
        return ResumeResponse.newBuilder()
                .setAgentInstanceId(session.getAgentInstanceId())
                .setStatus(session.getStatus().name())
                .setResumedAt(toEpochMilli(session.getResumedAt()))
                .build();
    }

    // ============ Helper ============

    private static String mapPhase(ReActPhaseType phase) {
        if (phase == null) return "";
        return phase.name().toLowerCase();
    }

    private static String mapStepStatus(StepStatus status) {
        if (status == null) return "";
        return status.name().toLowerCase();
    }

    private static String mapSessionStatus(SessionStatus status) {
        if (status == null) return "";
        return status.name();
    }

    private static boolean isTerminal(StepStatus status) {
        return status == StepStatus.SUCCESS || status == StepStatus.FAILED || status == StepStatus.CANCELLED;
    }

    private static long toEpochMilli(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }
}
