package com.agent.quality.enums;

/**
 * Task risk level (doc 11-detail-flow F9.D1, PRD §三(一)2).
 *
 * <p>LOW: 低风险任务（如 chitchat 闲聊），跳过 L4-2 事实一致性校验
 * MEDIUM: 中风险任务，执行完整 L4 三级校验
 * HIGH: 高风险任务，执行完整 L4 三级校验 + 强制人工审核</p>
 */
public enum TaskRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
