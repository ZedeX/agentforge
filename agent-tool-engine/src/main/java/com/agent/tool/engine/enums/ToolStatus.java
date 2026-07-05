package com.agent.tool.engine.enums;

/**
 * Tool lifecycle status (doc 01-database §4.1 tool_registry.status).
 *
 * <p>DRAFT=草稿, ENABLED=启用, OFFLINE=下线.</p>
 */
public enum ToolStatus {

    DRAFT(1),
    ENABLED(2),
    OFFLINE(3);

    private final int code;

    ToolStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ToolStatus fromCode(int code) {
        for (ToolStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown ToolStatus code: " + code);
    }
}
