package com.agent.session.model;

/**
 * 会话状态（对齐 doc 01-database §1.1）：
 *   1 活跃 / 2 空闲 / 3 关闭 / 4 归档
 */
public enum SessionStatus {

    ACTIVE(1, "active"),
    IDLE(2, "idle"),
    CLOSED(3, "closed"),
    ARCHIVED(4, "archived");

    private final int code;
    private final String apiValue;

    SessionStatus(int code, String apiValue) {
        this.code = code;
        this.apiValue = apiValue;
    }

    public int getCode() {
        return code;
    }

    public String getApiValue() {
        return apiValue;
    }

    public static SessionStatus fromCode(int code) {
        for (SessionStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown SessionStatus code: " + code);
    }
}
