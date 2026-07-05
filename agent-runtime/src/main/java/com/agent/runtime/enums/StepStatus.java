package com.agent.runtime.enums;

/**
 * Step 执行状态（doc 06-runtime §7.2，6 态）。
 *
 * <p>PENDING: 待执行（已创建未开始）
 * RUNNING: 执行中
 * SUCCESS: 执行成功
 * FAILED: 执行失败
 * RETRYING: 重试中（Reflexion 触发）
 * CANCELLED: 已取消（用户取消或上级取消）</p>
 */
public enum StepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    RETRYING,
    CANCELLED
}
