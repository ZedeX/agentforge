package com.agent.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 任务编排服务启动入口（端口 8084）。
 *
 * <p>当前阶段（T1-T4）仅包含 DAG 引擎 + 状态机的核心实现，
 * gRPC 服务端（TaskOrchestrator gRPC impl）与 RocketMQ 分发器留待 T5+。</p>
 */
@SpringBootApplication
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
