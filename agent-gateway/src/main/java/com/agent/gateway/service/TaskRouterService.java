package com.agent.gateway.service;

import com.agent.gateway.client.SessionServiceClient;
import com.agent.gateway.client.TaskOrchestratorClient;
import com.agent.gateway.dto.TaskCreateRequest;
import com.agent.gateway.dto.TaskCreateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 任务路由分发服务。
 *
 * 路由策略（来自 doc 00-overview §2.2 接入交互层）：
 *  - chat       -> session-service 同步对话
 *  - single_step -> 单 Agent：调用 task-orchestrator（complexity=L1）
 *  - complex     -> task-orchestrator.SubmitTask（complexity=L2/L3，async 异步）
 */
@Service
public class TaskRouterService {

    private static final Logger log = LoggerFactory.getLogger(TaskRouterService.class);

    private final TaskOrchestratorClient orchestratorClient;
    private final SessionServiceClient sessionServiceClient;

    public TaskRouterService(TaskOrchestratorClient orchestratorClient,
                             SessionServiceClient sessionServiceClient) {
        this.orchestratorClient = orchestratorClient;
        this.sessionServiceClient = sessionServiceClient;
    }

    public TaskCreateResponse route(TaskCreateRequest request, String tenantId, String userId) {
        String type = request.getType();
        return switch (type) {
            case "chat" -> handleChat(request, tenantId, userId);
            case "single_step" -> handleSingleStep(request, tenantId, userId);
            case "complex" -> handleComplex(request, tenantId, userId);
            default -> throw new IllegalArgumentException("Invalid task type: " + type);
        };
    }

    private TaskCreateResponse handleChat(TaskCreateRequest req, String tenantId, String userId) {
        String respId = sessionServiceClient.sendChat(req.getSessionId(), req.getGoal());
        log.info("route chat tenant={} user={} respId={}", tenantId, userId, respId);
        return new TaskCreateResponse(respId, "QUEUED");
    }

    private TaskCreateResponse handleSingleStep(TaskCreateRequest req, String tenantId, String userId) {
        String taskId = orchestratorClient.submitTask(tenantId, userId, req.getTitle(), req.getGoal());
        log.info("route single_step tenant={} user={} taskId={}", tenantId, userId, taskId);
        return new TaskCreateResponse(taskId, "PENDING");
    }

    private TaskCreateResponse handleComplex(TaskCreateRequest req, String tenantId, String userId) {
        String taskId = orchestratorClient.submitTask(tenantId, userId, req.getTitle(), req.getGoal());
        log.info("route complex tenant={} user={} taskId={} async={}", tenantId, userId, taskId, req.getAsync());
        return new TaskCreateResponse(taskId, "PENDING");
    }
}
