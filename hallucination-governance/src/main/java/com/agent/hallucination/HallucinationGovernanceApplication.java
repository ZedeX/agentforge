package com.agent.hallucination;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Hallucination Governance 服务启动类（F10 六层幻觉治理：自检 / RAG锚定 / 工具守卫 / 硬校验 / 指标追踪）。
 *
 * <p>HTTP 8106 / gRPC 9106（对齐 doc 00-overview §3.1）。
 * 端口配置见 application.yml。
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.agent.hallucination.config")
public class HallucinationGovernanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HallucinationGovernanceApplication.class, args);
    }
}
