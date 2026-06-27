package com.agent.gateway.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * task-orchestrator gRPC 客户端：
 *  - 待 agent-proto.task.proto 的 TaskOrchestratorGrpc 发布后，
 *    替换为 @GrpcClient("task-orchestrator") TaskOrchestratorFutureStub 注入。
 *  - 当前为本地降级 stub：生成 task_id 直接返回。
 */
@Component
public class TaskOrchestratorClient {

    private static final Logger log = LoggerFactory.getLogger(TaskOrchestratorClient.class);

    public String submitTask(String tenantId, String userId, String title, String goal) {
        String taskId = "tk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("submitTask stub tenant={} user={} title={} -> taskId={}", tenantId, userId, title, taskId);
        return taskId;
    }
}
