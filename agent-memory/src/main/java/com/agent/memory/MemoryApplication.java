package com.agent.memory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Agent Memory 服务启动类（F12 长期记忆：写入 / 提取 / 去重 / 蒸馏）。
 *
 * <p>HTTP 8088 / gRPC 9088（对齐 doc 00-overview §3.1）。
 * 端口配置见 application.yml。
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.agent.memory.config")
public class MemoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemoryApplication.class, args);
    }
}
