package com.agent.session.model;

/**
 * 消息角色（对齐 doc 01-database §1.2）：user / assistant / system / tool
 */
public enum MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}
