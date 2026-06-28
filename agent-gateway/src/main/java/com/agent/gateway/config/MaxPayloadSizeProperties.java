package com.agent.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MaxPayloadSizeFilter 配置：
 *  - 默认上限 1MB
 *  - 通过 gateway.max-payload.max-size 配置，支持值如 "1MB"、"512KB"、字节数
 */
@ConfigurationProperties(prefix = "gateway.max-payload")
public class MaxPayloadSizeProperties {

    /**
     * 默认上限 1MB（字节）。
     */
    private int maxSize = 1024 * 1024;

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }
}
