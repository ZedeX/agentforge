package com.agent.runtime.api.impl;

import com.agent.runtime.api.ReflexionEngine;
import com.agent.runtime.model.ReflectionFeedback;
import com.agent.runtime.model.RetryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reflexion 重试引擎默认实现 (doc 11-detail-flow F9.D5/D6, PRD §二(五)).
 *
 * <p>骨架阶段简单实现: retry() 构造 ReflectionFeedback (含 REFLECTION 提示),
 * isExhausted() 判断 retryCount > maxRetry.</p>
 */
@Component
public class ReflexionEngineImpl implements ReflexionEngine {

    private static final Logger log = LoggerFactory.getLogger(ReflexionEngineImpl.class);

    /** REFLECTION 提示前缀 */
    private static final String REFLECTION_PREFIX = "REFLECTION: ";

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
}
