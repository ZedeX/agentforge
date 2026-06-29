package com.agent.gateway.controller;

import com.agent.gateway.client.SessionServiceClient;
import com.agent.gateway.client.TaskOrchestratorClient;
import com.agent.gateway.service.TaskRouterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskControllerTest {

    private MockMvc mockMvc;
    private TaskOrchestratorClient orchestrator;
    private SessionServiceClient sessionClient;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        orchestrator = mock(TaskOrchestratorClient.class);
        sessionClient = mock(SessionServiceClient.class);

        when(orchestrator.submitTask(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn("tk_orch_complex_001");
        when(sessionClient.sendChat(nullable(String.class), nullable(String.class)))
                .thenReturn("ss_chat_resp_001");

        TaskRouterService router = new TaskRouterService(orchestrator, sessionClient);
        TaskController controller = new TaskController(router);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("type=chat 时应路由到 SessionService 并返回 ACCEPTED")
    void should_RouteChatToSessionService_When_TypeIsChat() throws Exception {
        String body = """
                {
                  "type": "chat",
                  "sessionId": "ss_abc",
                  "goal": "你好",
                  "priority": 5,
                  "async": false
                }
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType("application/json")
                        .header("X-Tenant-Id", "t_1")
                        .requestAttr("X-Tenant-Id", "t_1")
                        .requestAttr("X-User-Id", "u_test_001")
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskId").exists())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));

        // FN-013 整改：验证 chat 路由调用了 sessionClient.sendChat，未调用 orchestrator
        verify(sessionClient).sendChat("ss_abc", "你好");
        verifyNoInteractions(orchestrator);
    }

    @Test
    @DisplayName("type=single_step 时应路由到 Orchestrator 并返回 taskId=PENDING")
    void should_RouteSingleStepToOrchestrator_When_TypeIsSingleStep() throws Exception {
        String body = """
                {
                  "type": "single_step",
                  "goal": "查询订单状态",
                  "priority": 5,
                  "async": false
                }
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType("application/json")
                        .header("X-Tenant-Id", "t_1")
                        .requestAttr("X-Tenant-Id", "t_1")
                        .requestAttr("X-User-Id", "u_test_001")
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").value("tk_orch_complex_001"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // FN-013 整改：验证 single_step 路由调用了 orchestrator.submitTask，未调用 sessionClient
        verify(orchestrator).submitTask("t_1", "u_test_001", null, "查询订单状态");
        verifyNoInteractions(sessionClient);
    }

    @Test
    @DisplayName("type=complex 时应路由到 Orchestrator 并透传 title/goal/async/costLimit")
    void should_RouteComplexToOrchestrator_When_TypeIsComplex() throws Exception {
        String body = """
                {
                  "type": "complex",
                  "title": "周报生成",
                  "goal": "汇总本周销售数据生成周报",
                  "priority": 5,
                  "async": true,
                  "costLimitCent": 5000
                }
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType("application/json")
                        .header("X-Tenant-Id", "t_1")
                        .requestAttr("X-Tenant-Id", "t_1")
                        .requestAttr("X-User-Id", "u_test_001")
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").value("tk_orch_complex_001"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // FN-013 整改：验证 complex 路由调用了 orchestrator.submitTask 且参数透传正确
        verify(orchestrator).submitTask("t_1", "u_test_001", "周报生成", "汇总本周销售数据生成周报");
        verifyNoInteractions(sessionClient);
    }

    @Test
    @DisplayName("type=unknown 时应返回 400 INVALID_ARGUMENT 且不调用任何下游 client")
    void should_RejectRequest_When_TypeIsInvalid() throws Exception {
        String body = """
                {
                  "type": "unknown",
                  "goal": "x"
                }
                """;
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType("application/json")
                        .header("X-Tenant-Id", "t_1")
                        .requestAttr("X-Tenant-Id", "t_1")
                        .requestAttr("X-User-Id", "u_test_001")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        // FN-013 整改：非法 type 路由应在 router 层抛 IllegalArgumentException，不应调用任何下游 client
        verifyNoInteractions(orchestrator);
        verifyNoInteractions(sessionClient);
    }
}
