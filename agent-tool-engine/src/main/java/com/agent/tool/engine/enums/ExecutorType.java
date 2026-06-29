package com.agent.tool.engine.enums;

/**
 * Tool executor type (F8 risk classification input).
 *
 * <p>GENERAL: local in-process exec (R1).
 * PROXY: forward to external API (R2).
 * SANDBOX: containerized exec (R3).</p>
 */
public enum ExecutorType {
    GENERAL,
    PROXY,
    SANDBOX
}
