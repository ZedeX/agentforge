package com.agent.memory.enums;

/**
 * Task outcome for F12.D1 write decision (doc 04-memory §3.3).
 *
 * <ul>
 *   <li>SUCCESS — 任务成功完成</li>
 *   <li>FAILURE — 任务失败（触发 REFLECTIVE 记忆提取）</li>
 *   <li>PARTIAL — 部分成功（同时提取 PROCEDURAL + REFLECTIVE）</li>
 *   <li>TIMEOUT — 超时（触发 REFLECTIVE 记忆提取）</li>
 * </ul>
 */
public enum TaskOutcome {

    SUCCESS,
    FAILURE,
    PARTIAL,
    TIMEOUT
}
