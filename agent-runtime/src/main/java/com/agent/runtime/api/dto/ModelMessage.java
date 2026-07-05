package com.agent.runtime.api.dto;

/**
 * Chat message DTO (T4, doc 06-runtime §3).
 *
 * <p>Maps to {@code agentplatform.model.v1.Message}: role + content + optional tool_calls/tool_call_id.
 * Decouples runtime business code from generated proto classes.
 */
public class ModelMessage {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    private final String role;
    private final String content;
    private final ModelToolCall toolCall;
    private final String toolCallId;

    public ModelMessage(String role, String content) {
        this(role, content, null, null);
    }

    public ModelMessage(String role, String content, ModelToolCall toolCall, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCall = toolCall;
        this.toolCallId = toolCallId;
    }

    public static ModelMessage system(String content) {
        return new ModelMessage(ROLE_SYSTEM, content);
    }

    public static ModelMessage user(String content) {
        return new ModelMessage(ROLE_USER, content);
    }

    public static ModelMessage assistant(String content) {
        return new ModelMessage(ROLE_ASSISTANT, content);
    }

    public static ModelMessage tool(String toolCallId, String content) {
        return new ModelMessage(ROLE_TOOL, content, null, toolCallId);
    }

    public String getRole() { return role; }
    public String getContent() { return content; }
    public ModelToolCall getToolCall() { return toolCall; }
    public String getToolCallId() { return toolCallId; }
}
