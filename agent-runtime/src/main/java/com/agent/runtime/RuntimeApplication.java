package com.agent.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent Runtime 服务启动类（doc 06-runtime §1）。
 *
 * <p>端口：HTTP 8092 / gRPC 9092。
 * 对外暴露 AgentRuntime gRPC 服务 5 RPC（StartAgent / Step / GetState / Pause / Resume）。
 * 内部以 ReAct 循环（Think → Act → Observe → Reflexion）驱动 Agent 执行。</p>
 */
@SpringBootApplication(scanBasePackages = "com.agent.runtime")
@ConfigurationPropertiesScan(basePackages = "com.agent.runtime.config")
@EnableScheduling
@EnableAsync
public class RuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuntimeApplication.class, args);
    }
}
