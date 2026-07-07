package com.agent.quality;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Agent Quality 服务启动类（F9 L4 三级校验 / Badcase 归集 / 人工审核队列）。
 *
 * <p>HTTP 8100 / gRPC 9100（对齐 doc 02-api §3.1）。
 * 端口配置见 application.yml。
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.agent.quality.config")
public class QualityApplication {

    public static void main(String[] args) {
        SpringApplication.run(QualityApplication.class, args);
    }
}
