package com.agent.runtime.api.impl;

import com.agent.runtime.api.ReflexionEngine;
import com.agent.runtime.enums.ReflexionResult;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.ReflectionFeedback;
import com.agent.runtime.model.RetryContext;
import com.agent.runtime.model.StepState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reflexion 重试引擎默认实现 (doc 11-detail-flow F9.D5/D6, PRD §二(五), doc 06-runtime §3.2).
 *
 * <p>2026-07-05 T3 升级：新增 {@link #reflect} 方法实现 4 态决策逻辑。
 * 默认策略（可在 T6 替换为基于 LLM 的反思）：
     * <ul>
     *   <li>history 末步状态为 FAILED 且 retryCount &lt; maxRetry → RETRY</li>
     *   <li>retryCount &gt;= maxRetry → ABORT（重试耗尽）</li>
     *   <li>连续 3 步以上 FAILED → REPLAN（需重规划）</li>
     *   <li>其它 → CONTINUE</li>
     * </ul>
     * </p>
 *
 * <p>骨架阶段 {@link #retry} 构造 ReflectionFeedback（含 REFLECTION 提示），
 * {@link #isExhausted} 判断 retryCount &gt; maxRetry.</p>
 */
@Component
public class ReflexionEngineImpl implements ReflexionEngine {

    private static final Logger log = LoggerFactory.getLogger(ReflexionEngineImpl.class);

    /** REFLECTION 提示前缀 */
    private static final String REFLECTION_PREFIX = "REFLECTION: ";

    /** 连续失败次数达到该阈值时建议 REPLAN */
    private static final int REPLAN_FAILURE_THRESHOLD = 3;

    @Override
    public ReflectionFeedback retry(RetryContext retryContext, String validationFailure) {
        log.info("触发 Reflexion 重试: retryCount={}, maxRetry={}, failure={}",
                retryContext.getRetryCount(), retryContext.getMaxRetry(), validationFailure);

        // 自增重试计数
        retryContext.incrementRetryCount();
        int retryAttempt = retryContext.getRetryCount();

        String reflectionPrompt = REFLECTION_PREFIX + (validationFailure == null ? "unknown" : validationFailure);
        ReflectionFeedback feedback = new ReflectionFeedback(reflectionPrompt, validationFailure, retryAttempt);

        log.debug("Reflexion feedback 构造完成: retryAttempt={}, reflectionPrompt={}",
                retryAttempt, reflectionPrompt);
        return feedback;
    }

    @Override
    public boolean isExhausted(RetryContext retryContext) {
        if (retryContext == null) {
            log.warn("RetryContext 为 null, 视为已耗尽");
            return true;
        }
        boolean exhausted = retryContext.getRetryCount() > retryContext.getMaxRetry();
        log.debug("检查重试是否耗尽: retryCount={}, maxRetry={}, exhausted={}",
                retryContext.getRetryCount(), retryContext.getMaxRetry(), exhausted);
        return exhausted;
    }

    @Override
    public ReflexionResult reflect(ReActContext ctx, List<StepState> history, String triggerReason) {
        log.info("Reflexion 反思: agentInstanceId={}, stepNumber={}, retryCount={}, triggerReason={}",
                ctx.getAgentInstanceId(), ctx.getStepNumber(), ctx.getRetryCount(), triggerReason);

        // 1. retryCount 已达上限 → ABORT
        if (ctx.getRetryCount() >= ctx.getMaxRetry()) {
            log.warn("Reflexion 判定 ABORT: retryCount={} >= maxRetry={}",
                    ctx.getRetryCount(), ctx.getMaxRetry());
            return ReflexionResult.ABORT;
        }

        // 2. 连续失败次数 >= 阈值 → REPLAN
        int consecutiveFailures = countConsecutiveFailures(history);
        if (consecutiveFailures >= REPLAN_FAILURE_THRESHOLD) {
            log.warn("Reflexion 判定 REPLAN: consecutiveFailures={} >= threshold={}",
                    consecutiveFailures, REPLAN_FAILURE_THRESHOLD);
            return ReflexionResult.REPLAN;
        }

        // 3. triggerReason 含 "act_failure" → RETRY（当前步重试）
        if (triggerReason != null && triggerReason.startsWith("act_failure")) {
            log.info("Reflexion 判定 RETRY: act failure detected, triggerReason={}", triggerReason);
            return ReflexionResult.RETRY;
        }

        // 4. 默认 CONTINUE
        log.debug("Reflexion 判定 CONTINUE");
        return ReflexionResult.CONTINUE;
    }

    /** 从 history 末尾向前数连续 FAILED 步骤数 */
    private int countConsecutiveFailures(List<StepState> history) {
        if (history == null || history.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            StepState s = history.get(i);
            if (s != null && s.getStatus() != null
                    && "FAILED".equals(s.getStatus().name())) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
