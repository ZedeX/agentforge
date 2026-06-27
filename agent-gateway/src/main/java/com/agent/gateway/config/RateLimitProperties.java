package com.agent.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

    private int capacity = 20;
    private long refillTokens = 10;
    private long refillSeconds = 1;

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public long getRefillTokens() {
        return refillTokens;
    }

    public void setRefillTokens(long refillTokens) {
        this.refillTokens = refillTokens;
    }

    public long getRefillSeconds() {
        return refillSeconds;
    }

    public void setRefillSeconds(long refillSeconds) {
        this.refillSeconds = refillSeconds;
    }
}
