package com.agent.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "session.sse")
public class SseProperties {

    private String channelPrefix = "session";
    private long timeoutMs = 300000L;

    public String getChannelPrefix() {
        return channelPrefix;
    }

    public void setChannelPrefix(String channelPrefix) {
        this.channelPrefix = channelPrefix;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
