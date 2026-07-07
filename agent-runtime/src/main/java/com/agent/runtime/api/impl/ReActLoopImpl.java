package com.agent.runtime.api.impl;

import com.agent.runtime.api.MemoryClient;
import com.agent.runtime.api.ModelGatewayClient;
import com.agent.runtime.api.ReActLoop;
import com.agent.runtime.api.ReflexionEngine;
import com.agent.runtime.api.StepStateSyncer;
import com.agent.runtime.api.TokenWatermarkMonitor;
import com.agent.runtime.api.ToolEngineClient;
import com.agent.runtime.config.RuntimeProperties;
import com.agent.runtime.enums.ReActPhaseType;
import com.agent.runtime.enums.ReflexionResult;
import com.agent.runtime.enums.StepStatus;
import com.agent.runtime.exception.CircuitOpenException;
import com.agent.runtime.loop.ActPhase;
import com.agent.runtime.loop.ActResult;
import com.agent.runtime.loop.ObservePhase;
import com.agent.runtime.loop.ReActPromptBuilder;
import com.agent.runtime.loop.ThinkPhase;
import com.agent.runtime.loop.ThinkResult;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.ReActResult;
import com.agent.runtime.model.StepState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ReAct 循环控制器默认实现 (doc 06-runtime §2, F6 think/act/observe/finish).
 *
 * <p>2026-07-05 T3 完整实现：{@link #run} 编排 Think → Act → Observe → Reflexion 循环，
 * 支持 maxSteps 限制 / cancel 信号 / Reflexion 4 态（CONTINUE/RETRY/REPLAN/ABORT）。
 * 原 {@link #start} + {@link #transit} 保留向后兼容（旧测试使用）。</p>
 */
@Component
public class ReActLoopImpl implements ReActLoop {

    private static final Logger log = LoggerFactory.getLogger(ReActLoopImpl.class);

    /** S-09: JSON serializer for checkpoint data (thread-safe). */
    private static final ObjectMapper CHECKPOINT_MAPPER = new ObjectMapper();

    /** 骨架阶段最大循环次数, 超过触发熔断 (legacy, doc F6 CIRCUIT_OPEN) */
    private static final int MAX_LOOP_COUNT = 10;

    /** 骨架阶段默认循环上限: 3 轮 (THINK/ACT/OBSERVE 各一次), legacy start() 使用 */
    private static final int DEFAULT_LOOP_LIMIT = 3;

    private final ModelGatewayClient modelGateway;
    private final ToolEngineClient toolEngine;
    private final MemoryClient memoryClient;
    private final ReflexionEngine reflexionEngine;
    private final StepStateSyncer stepStateSyncer;
    private final TokenWatermarkMonitor tokenWatermarkMonitor;
    private final RuntimeProperties properties;

    /** T3 loop components (composition over inheritance) */
    private final ThinkPhase thinkPhase;
    private final ActPhase actPhase;
    private final ObservePhase observePhase;
    private final ReActPromptBuilder promptBuilder;

    @Autowired
    public ReActLoopImpl(ModelGatewayClient modelGateway,
                         ToolEngineClient toolEngine,
                         MemoryClient memoryClient,
                         ReflexionEngine reflexionEngine,
                         StepStateSyncer stepStateSyncer,
                         TokenWatermarkMonitor tokenWatermarkMonitor,
                         RuntimeProperties properties) {
        this.modelGateway = modelGateway;
        this.toolEngine = toolEngine;
        this.memoryClient = memoryClient;
        this.reflexionEngine = reflexionEngine;
        this.stepStateSyncer = stepStateSyncer;
        this.tokenWatermarkMonitor = tokenWatermarkMonitor;
        this.properties = properties;
        this.promptBuilder = new ReActPromptBuilder(properties);
        this.thinkPhase = new ThinkPhase(modelGateway, promptBuilder, tokenWatermarkMonitor);
        this.actPhase = new ActPhase(toolEngine);
        this.observePhase = new ObservePhase();
    }

    /**
     * Test-friendly constructor allowing direct injection of loop components.
     */
    public ReActLoopImpl(ModelGatewayClient modelGateway,
                         ToolEngineClient toolEngine,
                         MemoryClient memoryClient,
                         ReflexionEngine reflexionEngine,
                         StepStateSyncer stepStateSyncer,
                         TokenWatermarkMonitor tokenWatermarkMonitor,
                         RuntimeProperties properties,
                         ThinkPhase thinkPhase,
                         ActPhase actPhase,
                         ObservePhase observePhase,
                         ReActPromptBuilder promptBuilder) {
        this.modelGateway = modelGateway;
        this.toolEngine = toolEngine;
        this.memoryClient = memoryClient;
        this.reflexionEngine = reflexionEngine;
        this.stepStateSyncer = stepStateSyncer;
        this.tokenWatermarkMonitor = tokenWatermarkMonitor;
        this.properties = properties;
        this.thinkPhase = thinkPhase;
        this.actPhase = actPhase;
        this.observePhase = observePhase;
        this.promptBuilder = promptBuilder;
    }

    // ============ T3 run() full cycle ============

    @Override
    public ReActResult run(ReActContext ctx) {
        log.info("ReAct run start: agentInstanceId={}, taskId={}, maxSteps={}, tokenBudget={}",
                ctx.getAgentInstanceId(), ctx.getTaskId(), ctx.getMaxSteps(), ctx.getTokenBudget());

        List<StepState> history = new ArrayList<>();
        ctx.setPhase(ReActPhaseType.THINK);
        ctx.setStepNumber(0);

        while (true) {
            // 0. Check cancel signal
            if (ctx.isCancelled()) {
                log.info("ReAct run cancelled by external signal: stepNumber={}", ctx.getStepNumber());
                return ReActResult.cancelled(ctx.getAgentInstanceId(), ctx.getSessionId(),
                        ctx.getStepNumber(), ctx.getTokenUsed());
            }

            // 1. Check maxSteps
            if (ctx.getStepNumber() >= ctx.getMaxSteps()) {
                log.warn("ReAct run aborted: stepNumber={} >= maxSteps={}",
                        ctx.getStepNumber(), ctx.getMaxSteps());
                return ReActResult.aborted(ctx.getAgentInstanceId(), ctx.getSessionId(),
                        "MAX_STEPS_EXCEEDED", ctx.getStepNumber(), ctx.getTokenUsed());
            }

            // 2. Advance step
            ctx.incrementStepNumber();
            int currentStep = ctx.getStepNumber();
            log.info("ReAct step {} start: agentInstanceId={}", currentStep, ctx.getAgentInstanceId());

            // ===== Think phase =====
            StepState thinkState = beginStepState(ctx, currentStep, ReActPhaseType.THINK);
            String recalledMemory = memoryClient.recallMemory(
                    ctx.getAgentInstanceId(), ctx.getUserInput() != null ? ctx.getUserInput() : ctx.getTaskId());
            ThinkResult thinkResult = executeThink(ctx, history, recalledMemory);

            updateThinkState(thinkState, thinkResult);
            updateContextAfterThink(ctx, thinkResult);
            syncStepState(ctx, currentStep, ReActPhaseType.THINK, thinkResult.getThoughtContent());

            if (thinkResult.isFailed()) {
                history.add(thinkState);
                ReflexionResult r = reflexionEngine.reflect(ctx, history,
                        "think_failure:" + thinkResult.getErrorMessage());
                ReActResult terminal = handleReflexionResult(ctx, r, "think_failure");
                if (terminal != null) {
                    return terminal;
                }
                continue;
            }

            if (thinkResult.isFinished()) {
                log.info("ReAct run finished at step {}: finalAnswer={}",
                        currentStep, thinkResult.getFinalAnswer());
                history.add(thinkState);
                return ReActResult.success(ctx.getAgentInstanceId(), ctx.getSessionId(),
                        thinkResult.getFinalAnswer(), currentStep, ctx.getTokenUsed(), ctx.getCostUsedCent());
            }

            history.add(thinkState);

            // ===== Act phase =====
            StepState actState = beginStepState(ctx, currentStep, ReActPhaseType.ACT);
            ActResult actResult = executeAct(ctx, thinkResult.getToolCallDecision());
            updateActState(actState, thinkResult.getToolCallDecision(), actResult);
            syncStepState(ctx, currentStep, ReActPhaseType.ACT,
                    "tool_call:" + (thinkResult.getToolCallDecision() != null
                            ? thinkResult.getToolCallDecision().getToolId() : "null"));
            history.add(actState);

            // ===== Observe phase =====
            StepState observeState = beginStepState(ctx, currentStep, ReActPhaseType.OBSERVE);
            String observation = executeObserve(ctx, actResult);
            observeState.setObserveContent(observation);
            observeState.setStatus(StepStatus.SUCCESS);
            observeState.setEndedAt(Instant.now());
            syncStepState(ctx, currentStep, ReActPhaseType.OBSERVE, observation);
            history.add(observeState);

            // ===== Reflexion phase (every interval steps OR on Act failure) =====
            int interval = properties.getReflexion().getInterval();
            boolean shouldReflect = (currentStep % interval == 0) || !actResult.isSuccess();
            if (shouldReflect) {
                String triggerReason = actResult.isSuccess()
                        ? ("interval=" + interval)
                        : ("act_failure:" + actResult.getToolId());
                ReflexionResult r = reflexionEngine.reflect(ctx, history, triggerReason);
                ReActResult terminal = handleReflexionResult(ctx, r, triggerReason);
                if (terminal != null) {
                    return terminal;
                }
                // RETRY: re-run current step (decrement stepNumber so next iteration runs same step)
                if (r == ReflexionResult.RETRY) {
                    log.info("Reflexion RETRY: decrementing stepNumber to re-run step {}",
                            currentStep);
                    ctx.setStepNumber(currentStep - 1);
                }
            }
        }
    }

    /** Execute Think phase (extracted for refactor / test visibility). */
    ThinkResult executeThink(ReActContext ctx, List<StepState> history, String recalledMemory) {
        return thinkPhase.execute(ctx, history, recalledMemory);
    }

    /** Execute Act phase (extracted for refactor). */
    ActResult executeAct(ReActContext ctx, com.agent.runtime.loop.ToolCallDecision decision) {
        return actPhase.execute(ctx, decision);
    }

    /** Execute Observe phase (extracted for refactor). */
    String executeObserve(ReActContext ctx, ActResult actResult) {
        return observePhase.execute(ctx, actResult);
    }

    /** Handle Reflexion result; returns ReActResult if loop should terminate, null to continue. */
    ReActResult handleReflexionResult(ReActContext ctx, ReflexionResult r, String triggerReason) {
        switch (r) {
            case CONTINUE:
                log.debug("Reflexion CONTINUE: proceeding to next step");
                return null;
            case RETRY:
                ctx.incrementRetryCount();
                if (ctx.getRetryCount() > ctx.getMaxRetry()) {
                    log.warn("Reflexion RETRY exceeded maxRetry={}: aborting", ctx.getMaxRetry());
                    return ReActResult.aborted(ctx.getAgentInstanceId(), ctx.getSessionId(),
                            "RETRY_LIMIT_EXCEEDED:" + triggerReason,
                            ctx.getStepNumber(), ctx.getTokenUsed());
                }
                return null;  // continue loop, stepNumber will be decremented by caller
            case REPLAN:
                log.warn("Reflexion REPLAN: aborting current loop and notifying orchestrator");
                return ReActResult.replanRequested(ctx.getAgentInstanceId(), ctx.getSessionId(),
                        triggerReason, ctx.getStepNumber(), ctx.getTokenUsed());
            case ABORT:
                log.warn("Reflexion ABORT: terminating loop immediately");
                return ReActResult.aborted(ctx.getAgentInstanceId(), ctx.getSessionId(),
                        "REFLEXION_ABORT:" + triggerReason,
                        ctx.getStepNumber(), ctx.getTokenUsed());
            default:
                log.warn("Unknown ReflexionResult {}: treating as CONTINUE", r);
                return null;
        }
    }

    /** Begin a new StepState with THINK phase default fields. */
    private StepState beginStepState(ReActContext ctx, int stepNumber, ReActPhaseType phase) {
        StepState s = new StepState(ctx.getAgentInstanceId(), stepNumber, phase);
        s.setStepId(UUID.randomUUID().toString());
        s.setSessionId(ctx.getSessionId());
        s.setTenantId(ctx.getTenantId());
        s.setTaskId(ctx.getTaskId());
        s.setAgentDefinitionId(ctx.getAgentDefinitionId());
        s.setStatus(StepStatus.RUNNING);
        s.setStartedAt(Instant.now());
        return s;
    }

    private void updateThinkState(StepState state, ThinkResult result) {
        state.setThinkContent(result.getThoughtContent());
        state.setTokensUsed(result.getTokenUsage());
        state.setCostCent(result.getCostCent());
        state.setEndedAt(Instant.now());
        state.setStatus(result.isFailed() ? StepStatus.FAILED : StepStatus.SUCCESS);
        if (result.isFailed()) {
            state.setErrorMessage(result.getErrorMessage());
        }
    }

    private void updateContextAfterThink(ReActContext ctx, ThinkResult result) {
        ctx.addTokenUsed(result.getTokenUsage());
        ctx.addCostCent(result.getCostCent());
        if (result.isFinished()) {
            ctx.setFinalAnswer(result.getFinalAnswer());
            ctx.setPhase(ReActPhaseType.FINISH);
        }
    }

    private void updateActState(StepState state, com.agent.runtime.loop.ToolCallDecision decision,
                                ActResult actResult) {
        if (decision != null) {
            state.setActionTarget(decision.getToolId());
            state.setInputJson(decision.getParamsJson());
        }
        state.setOutputJson(actResult.getOutput());
        state.setTokensUsed(0);
        state.setDurationMs(actResult.getDurationMs());
        state.setEndedAt(Instant.now());
        state.setStatus(actResult.isSuccess() ? StepStatus.SUCCESS : StepStatus.FAILED);
        if (!actResult.isSuccess()) {
            state.setErrorMessage(actResult.getErrorMessage());
        }
    }

    /** Sync step state to StepStateSyncer and persist checkpoint.
     *  S-09: Uses Jackson for JSON serialization instead of String.format
     *  to properly escape special characters in detail field. */
    private void syncStepState(ReActContext ctx, int stepNumber, ReActPhaseType phase, String detail) {
        stepStateSyncer.syncStepState(ctx.getAgentInstanceId(), stepNumber, phase);
        try {
            var checkpoint = CHECKPOINT_MAPPER.createObjectNode();
            checkpoint.put("stepNumber", stepNumber);
            checkpoint.put("phase", phase != null ? phase.name() : null);
            checkpoint.put("detail", detail != null ? detail : "");
            checkpoint.put("tokenUsed", ctx.getTokenUsed());
            String checkpointData = CHECKPOINT_MAPPER.writeValueAsString(checkpoint);
            stepStateSyncer.checkpoint(ctx.getAgentInstanceId(), stepNumber, checkpointData);
        } catch (Exception e) {
            log.error("checkpoint 序列化失败: agentInstanceId={}, step={}, err={}",
                    ctx.getAgentInstanceId(), stepNumber, e.getMessage(), e);
        }
    }

    // ============ Legacy start() / transit() (backward compat) ============

    @Override
    @Deprecated
    public String start(ReActContext context) {
        log.info("Legacy start(): agentId={}, taskId={}", context.getAgentId(), context.getTaskId());
        context.setPhase(ReActPhaseType.THINK);

        String finalAnswer = null;
        for (int round = 0; round < DEFAULT_LOOP_LIMIT; round++) {
            context.incrementLoopCount();
            if (context.getLoopCount() > MAX_LOOP_COUNT) {
                log.warn("Loop count exceeded: loopCount={}", context.getLoopCount());
                throw new CircuitOpenException(context.getLoopCount());
            }

            String prompt = "round=" + round + ",taskId=" + context.getTaskId();
            String modelOutput = modelGateway.chat(prompt);
            log.debug("Round {} model output: {}", round, modelOutput);

            ReActPhaseType next = transit(context, modelOutput);
            context.setPhase(next);

            if (next == ReActPhaseType.FINISH) {
                finalAnswer = modelOutput;
                context.setFinalAnswer(finalAnswer);
                break;
            }

            if (next == ReActPhaseType.ACT && modelOutput != null && modelOutput.contains("tool_call")) {
                String toolResult = toolEngine.invoke("default_tool", "{}");
                context.getMemory().put("last_tool_result", toolResult);
                context.setPhase(ReActPhaseType.OBSERVE);
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "default-final-answer";
            context.setFinalAnswer(finalAnswer);
        }
        log.info("Legacy start() done: agentId={}, finalAnswer={}",
                context.getAgentId(), finalAnswer);
        return finalAnswer;
    }

    @Override
    @Deprecated
    public ReActPhaseType transit(ReActContext context, String modelOutput) {
        if (modelOutput == null) {
            log.warn("Model output null, default to FINISH");
            return ReActPhaseType.FINISH;
        }

        if (modelOutput.contains("final_answer")) {
            log.debug("Detected final_answer, transit to FINISH");
            return ReActPhaseType.FINISH;
        }
        if (modelOutput.contains("tool_call")) {
            log.debug("Detected tool_call, transit to ACT");
            return ReActPhaseType.ACT;
        }
        if (context.getPhase() == ReActPhaseType.ACT) {
            log.debug("ACT phase done, transit to OBSERVE");
            return ReActPhaseType.OBSERVE;
        }
        log.debug("Default transit to FINISH (modelOutput={})", modelOutput);
        return ReActPhaseType.FINISH;
    }
}
