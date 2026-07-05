package com.agent.tool.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent Tool Engine 服务启动类（F8 工具引擎：注册 / 风险 / 审批 / 沙箱 / 缓存 / 审计 / 召回 / 清洗）。
 *
 * <p>HTTP 8090 / gRPC 9090（对齐 doc 00-overview §3.1）。
 * 端口配置见 application.yml。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.agent.tool.engine.config")
@EnableScheduling
public class ToolEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToolEngineApplication.class, args);
    }
}
