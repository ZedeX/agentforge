package com.agent.tool.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent Tool Engine 服务启动类（F8 工具引擎：注册 / 风险 / 审批 / 沙箱 / 缓存 / 审计 / 召回 / 清洗）。
 *
 * <p>HTTP 8090 / gRPC 9090（对齐 doc 00-overview §3.1）。
 * 端口配置见 application.yml。</p>
 *
 * <p>S-04: {@code @EntityScan} and {@code @EnableJpaRepositories} include
 * {@code com.agent.common.outbox} so that {@link com.agent.common.outbox.OutboxMessage}
 * and {@link com.agent.common.outbox.OutboxRepository} are available in this service.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.agent.tool.engine.config")
@EnableScheduling
public class ToolEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToolEngineApplication.class, args);
    }
}
