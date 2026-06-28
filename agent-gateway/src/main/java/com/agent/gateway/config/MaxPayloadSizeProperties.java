package com.agent.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * MaxPayloadSizeFilter 配置：
 *  - 默认上限 1MB
 *  - 通过 gateway.max-payload.max-size 配置，支持值如 "1MB"、"512KB"、"10B"、字节数
 *  - 使用 Spring DataSize 自动绑定（支持 B/KB/MB/GB 后缀）
 */
@ConfigurationProperties(prefix = "gateway.max-payload")
public class MaxPayloadSizeProperties {

    /**
     * 默认上限 1MB。
     */
    private DataSize maxSize = DataSize.ofMegabytes(1);

    public DataSize getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(DataSize maxSize) {
        this.maxSize = maxSize;
    }
}
