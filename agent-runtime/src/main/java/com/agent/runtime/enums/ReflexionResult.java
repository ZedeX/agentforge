package com.agent.runtime.enums;

/**
 * Reflexion retry outcome (doc 11-detail-flow F9.D5/D6, PRD §二(五) Reflexion).
 *
 * <p>RETRY: L4 校验失败, 注入 REFLECTION 提示重试 (retry_count+1, 上限 2)
 * EXHAUSTED: retry_count > 2, 抛 MAX_RETRY_EXCEEDED, 转人工审核
 * RESET: 重试成功, 重置 retry_count=0</p>
 */
public enum ReflexionResult {
    RETRY,
    EXHAUSTED,
    RESET
}
