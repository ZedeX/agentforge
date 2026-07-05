package com.agent.runtime.api;

import com.agent.runtime.enums.ReActPhaseType;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.ReActResult;

/**
 * ReAct loop controller (doc 06-runtime §2, F6 think/act/observe/finish).
 *
 * <p>2026-07-05 T3 升级：新增 {@link #run} 方法返回结构化 {@link ReActResult}
 * (含 finalAnswer / stepCount / tokenUsage / status)，支撑 Plan 06 T3 完整循环。
 * 原 {@link #start} + {@link #transit} 保留向后兼容（ReActLoopImplTest 旧测试 + F6DecisionNodeTest 仍使用）。</p>
 */
public interface ReActLoop {

    /**
     * Start ReAct loop for given task context (legacy, kept for backward compat).
     *
     * @param context ReAct execution context
     * @return final answer string
     * @deprecated use {@link #run(ReActContext)} which returns structured ReActResult
     */
    @Deprecated
    String start(ReActContext context);

    /**
     * Transit to next phase based on model output (legacy, kept for backward compat).
     *
     * @param context current context
     * @param modelOutput model generation output (thought / tool_call / final_answer)
     * @return next phase
     * @deprecated phase transition is now internal to {@link #run}
     */
    @Deprecated
    ReActPhaseType transit(ReActContext context, String modelOutput);

    /**
     * Run full ReAct loop: Think → Act → Observe → Reflexion → next step (T3 new).
     *
     * <p>Loop terminates when:
     * <ul>
     *   <li>Think produces final_answer → SUCCESS</li>
     *   <li>stepNumber exceeds maxSteps → ABORTED (MAX_STEPS_EXCEEDED)</li>
     *   <li>Reflexion returns REPLAN → REPLAN_REQUESTED</li>
     *   <li>Reflexion returns ABORT → ABORTED</li>
     *   <li>External cancel signal → CANCELLED</li>
     * </ul>
     *
     * @param ctx ReAct execution context (must have agentInstanceId / taskId / maxSteps / tokenBudget set)
     * @return ReActResult with final answer (if any), step count, token usage, and status
     */
    ReActResult run(ReActContext ctx);
}
