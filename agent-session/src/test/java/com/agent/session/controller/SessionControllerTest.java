package com.agent.session.controller;

import com.agent.session.model.Message;
import com.agent.session.model.MessageRole;
import com.agent.session.model.Session;
import com.agent.session.service.SessionService;
import com.agent.session.service.ShortTermMemoryService;
import com.agent.session.testinfra.fixture.SessionFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SessionController 单元测试。
 *
 * <p>覆盖：创建 / 查询 / 关闭 / 发消息 / 分页查消息 / 参数校验 / 异常路径。</p>
 *
 * <p>整改记录：</p>
 * <ul>
 *   <li>FN-011：复用 {@link SessionFixtures#aSession(String)} 替代类内私有 {@code newSession} 方法；</li>
 *   <li>FN-013：在 shouldCreateSession / shouldCloseSession / shouldSendMessage 中补 {@code verify()}
 *       交互断言，确认 controller 调用 service 的参数与次数符合契约；</li>
 *   <li>FN-009：新增 shouldThrowWhenServiceThrows，用 {@code assertThrows} 验证异常向上冒泡不被吞。</li>
 *   <li>P6-3/4/5：方法名统一为 {@code should_Xxx_When_Yyy}；JUnit 断言替换为 AssertJ；补充中文 @DisplayName。</li>
 * </ul>
 */
class SessionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        ShortTermMemoryService memory = mock(ShortTermMemoryService.class);
        SessionController controller = new SessionController(sessionService, memory);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("创建会话请求应返回 active 状态的 Session 并透传租户/用户/agent/title")
    void should_CreateSession_When_ValidRequest() throws Exception {
        Session s = SessionFixtures.aSession("ss_new_001");
        when(sessionService.createSession(any(), any(), any(), any())).thenReturn(s);

        String body = """
                {
                  "agentId": 2001,
                  "title": "测试会话",
                  "meta": { "channel": "web" }
                }
                """;

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "1001")
                        .header("X-User-Id", "u_001")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.sessionId").value("ss_new_001"))
                .andExpect(jsonPath("$.data.status").value("active"));

        // FN-013 整改：验证 controller 将租户/用户/agent/title 正确透传给 service
        verify(sessionService).createSession(1001L, "u_001", 2001L, "测试会话");
    }

    @Test
    @DisplayName("查询已存在的 Session 应返回对应数据")
    void should_ReturnSession_When_GetExistingSession() throws Exception {
        Session s = SessionFixtures.aSession("ss_get_001");
        when(sessionService.getSession("ss_get_001")).thenReturn(Optional.of(s));

        mockMvc.perform(get("/api/v1/sessions/ss_get_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("ss_get_001"));

        verify(sessionService).getSession("ss_get_001");
    }

    @Test
    @DisplayName("查询不存在的 Session 应返回 404 与 SESSION_NOT_FOUND 错误码")
    void should_Return404_When_SessionNotFound() throws Exception {
        when(sessionService.getSession("ss_missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/sessions/ss_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));

        verify(sessionService).getSession("ss_missing");
    }

    @Test
    @DisplayName("关闭已存在的 Session 应返回 OK")
    void should_CloseSession_When_ValidRequest() throws Exception {
        when(sessionService.closeSession("ss_close_001")).thenReturn(true);

        mockMvc.perform(delete("/api/v1/sessions/ss_close_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(sessionService).closeSession("ss_close_001");
    }

    @Test
    @DisplayName("关闭不存在的 Session 应返回 404 与 SESSION_NOT_FOUND 错误码")
    void should_Return404_When_ClosingMissingSession() throws Exception {
        when(sessionService.closeSession("ss_missing")).thenReturn(false);

        mockMvc.perform(delete("/api/v1/sessions/ss_missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));

        verify(sessionService).closeSession("ss_missing");
    }

    @Test
    @DisplayName("向 active 会话发消息应返回 assistant 回复")
    void should_SendMessageAndReturnReply_When_ValidRequest() throws Exception {
        Message reply = SessionFixtures.aMessage("ss_msg_001", MessageRole.ASSISTANT, "已收到");
        reply.setTokenCount(5);
        when(sessionService.sendMessage(eq("ss_msg_001"), anyString(), any(), any()))
                .thenReturn(reply);

        String body = """
                {
                  "content": "你好",
                  "contentType": "text"
                }
                """;

        mockMvc.perform(post("/api/v1/sessions/ss_msg_001/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.messageId").value("msg_ss_msg_001"))
                .andExpect(jsonPath("$.data.role").value("assistant"));

        verify(sessionService).sendMessage(eq("ss_msg_001"), eq("你好"), eq("text"), eq("anonymous"));
    }

    @Test
    @DisplayName("分页查询消息应返回对应分页数据")
    void should_ReturnPaginatedMessages_When_ListMessages() throws Exception {
        Message m1 = SessionFixtures.aMessage("ss_list_001", MessageRole.USER, "hi");
        m1.setTokenCount(2);
        m1.setCreatedAt(Instant.now());

        when(sessionService.listMessages(eq("ss_list_001"), any()))
                .thenReturn(new PageImpl<>(List.of(m1), PageRequest.of(0, 20, Sort.by("createdAt").ascending()), 1));

        mockMvc.perform(get("/api/v1/sessions/ss_list_001/messages")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].msgId").value("msg_ss_list_001"))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(sessionService).listMessages(eq("ss_list_001"), any());
    }

    @Test
    @DisplayName("content 为空时请求应被拒绝并返回 INVALID_ARGUMENT 错误码")
    void should_ReturnBadRequest_When_ContentEmpty() throws Exception {
        String body = """
                {
                  "content": "",
                  "contentType": "text"
                }
                """;

        mockMvc.perform(post("/api/v1/sessions/ss_bad_001/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    /**
     * FN-009 整改：直接单元测试 controller 依赖的 service 抛异常时未被吞掉，能正常向上冒泡。
     * 此用例绕过 MockMvc，直接调用 controller 方法，用 AssertJ 断言异常类型。
     */
    @Test
    @DisplayName("Service 抛出 IllegalStateException 时 controller 不应吞掉异常")
    void should_ThrowIllegalStateException_When_ServiceThrowsException() {
        SessionService throwingService = mock(SessionService.class);
        ShortTermMemoryService memory = mock(ShortTermMemoryService.class);
        when(throwingService.getSession("ss_throw"))
                .thenThrow(new IllegalStateException("redis connection lost"));
        SessionController controller = new SessionController(throwingService, memory);

        // controller 未捕获 IllegalStateException，应直接冒泡到调用方
        assertThatThrownBy(() -> controller.getSession("ss_throw"))
                .isInstanceOf(IllegalStateException.class);
    }
}
