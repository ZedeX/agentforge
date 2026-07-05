package com.agent.runtime.enums;

/**
 * Agent 会话状态（doc 06-runtime §7.2 + proto AgentState.status）。
 *
 * <p>CREATING: 会话创建中（StartAgent 入口）
 * RUNNING: 会话运行中
 * PAUSED: 已暂停（Pause RPC 触发）
 * SUCCESS: 会话成功完成
 * FAILED: 会话失败终止
 * CANCELLED: 会话已取消</p>
 */
public enum SessionStatus {
    CREATING,
    RUNNING,
    PAUSED,
    SUCCESS,
    FAILED,
    CANCELLED
}
