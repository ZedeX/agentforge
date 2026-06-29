package com.agent.runtime.enums;

/**
 * ReAct loop phase (doc 11-detail-flow F6, doc 06 §2 ReAct 循环).
 *
 * <p>THINK: model 生成 thought + 可选 tool_call
 * ACT: 执行工具调用 (tool-engine.Invoke)
 * OBSERVE: 工具结果注入上下文
 * FINISH: 产出最终答案, 终止循环</p>
 */
public enum ReActPhaseType {
    THINK,
    ACT,
    OBSERVE,
    FINISH
}
