package com.agent.common.constant;

/**
 * Agent 生命周期状态（doc 02-api §2.1）
 */
public enum AgentStatus {

    DRAFT(0),
    ONLINE(1),
    OFFLINE(2),
    SUSPENDED(3);

    private final int code;

    AgentStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static AgentStatus fromCode(int code) {
        for (AgentStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown AgentStatus code: " + code);
    }
}
