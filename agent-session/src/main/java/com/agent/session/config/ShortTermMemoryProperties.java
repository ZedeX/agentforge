package com.agent.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "session.memory")
public class ShortTermMemoryProperties {

    private String keyPrefix = "sm";
    private long ttlHours = 24;
    private int maxRecentMessages = 20;

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public long getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(long ttlHours) {
        this.ttlHours = ttlHours;
    }

    public int getMaxRecentMessages() {
        return maxRecentMessages;
    }

    public void setMaxRecentMessages(int maxRecentMessages) {
        this.maxRecentMessages = maxRecentMessages;
    }
}
