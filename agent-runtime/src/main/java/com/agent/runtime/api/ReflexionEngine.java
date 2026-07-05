package com.agent.runtime.api;

import com.agent.runtime.enums.ReflexionResult;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.ReflectionFeedback;
import com.agent.runtime.model.RetryContext;
import com.agent.runtime.model.StepState;

import java.util.List;

/**
 * Reflexion retry engine (doc 11-detail-flow F9.D5/D6, PRD §二(五), doc 06-runtime §3.2).
 *
 * <p>2026-07-05 T3 升级：新增 {@link #reflect} 方法返回 4 态 {@code ReflexionResult}
 * (CONTINUE/RETRY/REPLAN/ABORT)，支撑 ReActLoop 的 Reflexion 阶段决策。
 * 原 {@link #retry} + {@link #isExhausted} 保留向后兼容（F6DecisionNodeTest UT-RT-005/006 仍使用）。</p>
 */
public interface ReflexionEngine {

    /**
     * Inject REFLECTION prompt and retry (legacy, kept for backward compat with UT-RT-005).
     *
     * @param retryContext     retry state
     * @param validationFailure L4 validation failure reason
     * @return reflection feedback for next retry
     */
    ReflectionFeedback retry(RetryContext retryContext, String validationFailure);

    /**
     * Check if retry limit exceeded (legacy, kept for backward compat with UT-RT-006).
     *
     * @param retryContext retry state
     * @return true if retry_count > max_retry (2)
     */
    boolean isExhausted(RetryContext retryContext);

    /**
     * Reflect on current ReAct execution state and decide next action (T3 new).
     *
     * <p>4 态决策（doc 06-runtime §3.2）：
     * <ul>
     *   <li>CONTINUE: 继续下一步（无异常或质量达标）</li>
     *   <li>RETRY: 重试当前步（retryCount+1，超限 → ABORT）</li>
     *   <li>REPLAN: 请求重规划（通知 task-orchestrator + ABORT 当前循环）</li>
     *   <li>ABORT: 立即终止（不可恢复）</li>
     * </ul>
     *
     * @param ctx           ReAct context (carries retryCount / maxRetry)
     * @param history       accumulated step states for reflection analysis
     * @param triggerReason why reflection is invoked (e.g. "interval=3", "act_failure:tool_xxx")
     * @return ReflexionResult instructing loop how to proceed
     */
    ReflexionResult reflect(ReActContext ctx, List<StepState> history, String triggerReason);
}
