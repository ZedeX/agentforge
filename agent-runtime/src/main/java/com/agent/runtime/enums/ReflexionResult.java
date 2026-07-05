package com.agent.runtime.enums;

/**
 * Reflexion 反思结果（doc 06-runtime §3.2，4 态）。
 *
 * <p>CONTINUE: 继续下一步（无异常）
 * RETRY: 重试当前步（retryCount+1，超限 → ABORT）
 * REPLAN: 请求重规划（通知 task-orchestrator + ABORT 当前循环）
 * ABORT: 立即终止（不可恢复）</p>
 *
 * <p>2026-07-05 升级：原 RETRY/EXHAUSTED/RESET 三态替换为 CONTINUE/RETRY/REPLAN/ABORT 四态
 * （对齐 doc 06-runtime §3.2 + Plan 06 T3 要求）。原 EXHAUSTED 语义合并到 ABORT。</p>
 */
public enum ReflexionResult {
    /** 继续下一步（无异常或质量达标） */
    CONTINUE,
    /** 重试当前步（retryCount+1，超限 → ABORT） */
    RETRY,
    /** 请求重规划（通知 orchestrator + ABORT 当前循环） */
    REPLAN,
    /** 立即终止（不可恢复，含原 EXHAUSTED 语义） */
    ABORT
}
