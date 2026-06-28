package com.agent.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TaskCreateRequest {

    @NotBlank
    private String type;            // chat / single_step / complex

    @NotBlank
    private String goal;

    private String title;
    private String sessionId;
    private Integer priority = 5;
    private Boolean async = false;
    private Long costLimitCent;

    /**
     * UT-F1-001: 内部调用标记。
     * - REST 入口构造的 TaskCreateRequest 默认 false（走 JWT/API-Key 鉴权）
     * - gRPC 内部调用经 ProtocolAdapter.adapt() 适配后置 true（走 mTLS，跳过 JWT）
     */
    private Boolean internal = false;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getAsync() {
        return async;
    }

    public void setAsync(Boolean async) {
        this.async = async;
    }

    public Long getCostLimitCent() {
        return costLimitCent;
    }

    public void setCostLimitCent(Long costLimitCent) {
        this.costLimitCent = costLimitCent;
    }

    public Boolean getInternal() {
        return internal;
    }

    public void setInternal(Boolean internal) {
        this.internal = internal;
    }
}
