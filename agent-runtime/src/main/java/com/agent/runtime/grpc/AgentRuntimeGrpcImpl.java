package com.agent.runtime.grpc;

import agentplatform.agent_runtime.v1.AgentRuntimeGrpc;
import agentplatform.agent_runtime.v1.AgentState;
import agentplatform.agent_runtime.v1.GetStateRequest;
import agentplatform.agent_runtime.v1.PauseRequest;
import agentplatform.agent_runtime.v1.PauseResponse;
import agentplatform.agent_runtime.v1.ResumeRequest;
import agentplatform.agent_runtime.v1.ResumeResponse;
import agentplatform.agent_runtime.v1.StartAgentRequest;
import agentplatform.agent_runtime.v1.StartAgentResponse;
import agentplatform.agent_runtime.v1.StepRequest;
import agentplatform.agent_runtime.v1.StepResponse;
import com.agent.runtime.api.ReActLoop;
import com.agent.runtime.api.impl.SessionManager;
import com.agent.runtime.enums.SessionStatus;
import com.agent.runtime.model.AgentSession;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.ReActResult;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AgentRuntime gRPC service implementation (T10, doc 06 §7).
 *
 * <p>Implements 5 RPCs:
 * <ul>
 *   <li>{@link #startAgent} — create session + launch ReAct loop</li>
 *   <li>{@link #step} — execute one step of the ReAct loop</li>
 *   <li>{@link #getState} — query session state</li>
 *   <li>{@link #pause} — pause a running session</li>
 *   <li>{@link #resume} — resume a paused session</li>
 * </ul>
 *
 * <p>Each RPC follows the pattern: unmarshal → delegate → marshal,
 * with exceptions translated via {@link GrpcExceptionAdvice}.</p>
 */
@GrpcService
public class AgentRuntimeGrpcImpl extends AgentRuntimeGrpc.AgentRuntimeImplBase {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeGrpcImpl.class);

    private final SessionManager sessionManager;
    private final ReActLoop reActLoop;
    private final GrpcExceptionAdvice advice;

    public AgentRuntimeGrpcImpl(SessionManager sessionManager,
                                ReActLoop reActLoop,
                                GrpcExceptionAdvice advice) {
        this.sessionManager = sessionManager;
        this.reActLoop = reActLoop;
        this.advice = advice;
    }

    @Override
    public void startAgent(StartAgentRequest request, StreamObserver<StartAgentResponse> observer) {
        try {
            validateStartAgentRequest(request);

            AgentSession session = sessionManager.startSession(
                    request.getAgentId(),
                    request.getAgentVersion(),
                    request.getTaskId(),
                    request.getSubtaskId(),
                    request.getNodeId(),
                    request.getInputsJson(),
                    request.getMaxSteps(),
                    request.getTokenBudget(),
                    request.getCostBudgetCent());

            log.info("startAgent: agentInstanceId={}, agentId={}, taskId={}",
                    session.getAgentInstanceId(), request.getAgentId(), request.getTaskId());

            StartAgentResponse response = StepStateMapper.toStartAgentResponse(session);
            observer.onNext(response);
            observer.onCompleted();
        } catch (Exception e) {
            advice.translate(e, observer);
        }
    }

    @Override
    public void step(StepRequest request, StreamObserver<StepResponse> observer) {
        try {
            String agentInstanceId = request.getAgentInstanceId();
            if (agentInstanceId == null || agentInstanceId.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "agent_instance_id is required");
            }

            AgentSession session = sessionManager.getSession(agentInstanceId);

            // Build ReActContext from session
            ReActContext ctx = new ReActContext(
                    session.getAgentInstanceId(),
                    session.getSessionId(),
                    session.getTaskId(),
                    session.getMaxSteps(),
                    session.getTokenBudget());
            ctx.setStepNumber(session.getCurrentStepNumber());
            ctx.setTokenUsed(session.getTokenUsed());
            ctx.setCostUsedCent(session.getCostUsedCent());

            // Execute one full step (think → act → observe)
            ReActResult result = reActLoop.run(ctx);

            // Update session usage
            int tokenDelta = result.getTotalTokensUsed();
            long costDelta = result.getTotalCostCent();
            if (tokenDelta > 0 || costDelta > 0) {
                sessionManager.updateUsage(agentInstanceId, tokenDelta, costDelta);
            }

            // If step indicates completion, finish session
            if (result.isFinished()) {
                SessionStatus terminalStatus = "SUCCESS".equals(result.getStatus())
                        ? SessionStatus.SUCCESS : SessionStatus.FAILED;
                sessionManager.finishSession(agentInstanceId, terminalStatus,
                        result.getReplanReason());
            }

            // Build StepResponse from ReActResult
            StepResponse response = StepResponse.newBuilder()
                    .setAgentInstanceId(agentInstanceId)
                    .setStepNo(session.getCurrentStepNumber() + 1)
                    .setPhase("think")
                    .setTokenUsed(result.getTotalTokensUsed())
                    .setCostCent(result.getTotalCostCent())
                    .setStatus(result.getStatus().toLowerCase())
                    .setFinished(result.isFinished())
                    .build();

            observer.onNext(response);
            observer.onCompleted();
        } catch (Exception e) {
            advice.translate(e, observer);
        }
    }

    @Override
    public void getState(GetStateRequest request, StreamObserver<AgentState> observer) {
        try {
            String agentInstanceId = request.getAgentInstanceId();
            if (agentInstanceId == null || agentInstanceId.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "agent_instance_id is required");
            }

            AgentSession session = sessionManager.getSession(agentInstanceId);
            AgentState state = StepStateMapper.toAgentState(session);
            observer.onNext(state);
            observer.onCompleted();
        } catch (Exception e) {
            advice.translate(e, observer);
        }
    }

    @Override
    public void pause(PauseRequest request, StreamObserver<PauseResponse> observer) {
        try {
            String agentInstanceId = request.getAgentInstanceId();
            if (agentInstanceId == null || agentInstanceId.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "agent_instance_id is required");
            }

            AgentSession session = sessionManager.pauseSession(agentInstanceId, request.getReason());
            log.info("pause: agentInstanceId={}", agentInstanceId);

            PauseResponse response = StepStateMapper.toPauseResponse(session);
            observer.onNext(response);
            observer.onCompleted();
        } catch (Exception e) {
            advice.translate(e, observer);
        }
    }

    @Override
    public void resume(ResumeRequest request, StreamObserver<ResumeResponse> observer) {
        try {
            String agentInstanceId = request.getAgentInstanceId();
            if (agentInstanceId == null || agentInstanceId.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "agent_instance_id is required");
            }

            AgentSession session = sessionManager.resumeSession(agentInstanceId);
            log.info("resume: agentInstanceId={}", agentInstanceId);

            ResumeResponse response = StepStateMapper.toResumeResponse(session);
            observer.onNext(response);
            observer.onCompleted();
        } catch (Exception e) {
            advice.translate(e, observer);
        }
    }

    private void validateStartAgentRequest(StartAgentRequest request) {
        if (request.getAgentId() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "agent_id must be positive");
        }
        if (request.getTaskId() == null || request.getTaskId().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "task_id is required");
        }
        if (request.getMaxSteps() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "max_steps must be positive");
        }
        if (request.getTokenBudget() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "token_budget must be positive");
        }
    }
}
