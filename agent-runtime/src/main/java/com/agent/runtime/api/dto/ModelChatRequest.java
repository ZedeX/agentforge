package com.agent.runtime.api.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured chat request DTO (T4, doc 06-runtime §3).
 *
 * <p>Maps to {@code agentplatform.model.v1.ChatRequest}: model + messages + params + scene/tier.
 * Used by {@code ModelGatewayClient.chat(ModelChatRequest)} and {@code chatStream(...)}.
 *
 * <p>Builder-style construction keeps call sites readable while remaining a plain POJO
 * (no Lombok to stay consistent with existing runtime DTO style).
 */
public class ModelChatRequest {

    private String callId;
    private String taskId;
    private String scene;          // intent | planning | tool_call | summary | audit
    private String tier;           // light | middle | strong
    private String preferredModel;
    private List<ModelMessage> messages = new ArrayList<>();
    private double temperature = 0.7;
    private int maxTokens = 1024;
    private double topP = 1.0;
    private List<String> stop = new ArrayList<>();
    private boolean enableCot = false;
    private boolean requireSource = false;
    private boolean enablePromptCache = false;
    private boolean stream = false;
    private long timeoutMs = 30_000L;

    public static Builder builder() { return new Builder(); }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public String getPreferredModel() { return preferredModel; }
    public void setPreferredModel(String preferredModel) { this.preferredModel = preferredModel; }
    public List<ModelMessage> getMessages() { return Collections.unmodifiableList(messages); }
    public void setMessages(List<ModelMessage> messages) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public double getTopP() { return topP; }
    public void setTopP(double topP) { this.topP = topP; }
    public List<String> getStop() { return Collections.unmodifiableList(stop); }
    public void setStop(List<String> stop) {
        this.stop = stop == null ? new ArrayList<>() : new ArrayList<>(stop);
    }
    public boolean isEnableCot() { return enableCot; }
    public void setEnableCot(boolean enableCot) { this.enableCot = enableCot; }
    public boolean isRequireSource() { return requireSource; }
    public void setRequireSource(boolean requireSource) { this.requireSource = requireSource; }
    public boolean isEnablePromptCache() { return enablePromptCache; }
    public void setEnablePromptCache(boolean enablePromptCache) { this.enablePromptCache = enablePromptCache; }
    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

    public static class Builder {
        private final ModelChatRequest req = new ModelChatRequest();

        public Builder callId(String v) { req.callId = v; return this; }
        public Builder taskId(String v) { req.taskId = v; return this; }
        public Builder scene(String v) { req.scene = v; return this; }
        public Builder tier(String v) { req.tier = v; return this; }
        public Builder preferredModel(String v) { req.preferredModel = v; return this; }
        public Builder message(ModelMessage m) { req.messages.add(m); return this; }
        public Builder messages(List<ModelMessage> m) { req.messages.addAll(m); return this; }
        public Builder systemMessage(String content) { req.messages.add(ModelMessage.system(content)); return this; }
        public Builder userMessage(String content) { req.messages.add(ModelMessage.user(content)); return this; }
        public Builder temperature(double v) { req.temperature = v; return this; }
        public Builder maxTokens(int v) { req.maxTokens = v; return this; }
        public Builder topP(double v) { req.topP = v; return this; }
        public Builder stop(List<String> v) { req.stop.addAll(v); return this; }
        public Builder enableCot(boolean v) { req.enableCot = v; return this; }
        public Builder requireSource(boolean v) { req.requireSource = v; return this; }
        public Builder enablePromptCache(boolean v) { req.enablePromptCache = v; return this; }
        public Builder stream(boolean v) { req.stream = v; return this; }
        public Builder timeoutMs(long v) { req.timeoutMs = v; return this; }

        public ModelChatRequest build() { return req; }
    }
}
