package com.agent.gateway.filter;

import com.agent.gateway.client.RiskControlClient;
import com.agent.gateway.dto.SafetyCheckResult;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSafetyFilterTest {

    private ContentSafetyFilter filter;

    @BeforeEach
    void setUp() {
        RiskControlClient client = new RiskControlClient() {
            @Override
            public SafetyCheckResult preCheck(String tenantId, String userId, String content) {
                if (content != null && content.contains("违规词")) {
                    return new SafetyCheckResult("BLOCK", "CONTENT_BLOCKED", "命中违规词");
                }
                return new SafetyCheckResult("PASS", null, null);
            }
        };
        filter = new ContentSafetyFilter(client);
    }

    @Test
    @DisplayName("内容命中违规词时应返回 400 CONTENT_BLOCKED 且中断过滤器链")
    void should_BlockRequest_When_ContentViolatesRules() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req.setContent("{\"goal\":\"包含违规词的内容\"}".getBytes());
        req.setContentType("application/json");
        req.setAttribute("X-Tenant-Id", "t_1");
        req.setAttribute("X-User-Id", "u_1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(resp.getHeader("X-Error-Code")).isEqualTo("CONTENT_BLOCKED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("内容正常时应返回 200 且放行过滤器链")
    void should_PassRequest_When_ContentClean() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req.setContent("{\"goal\":\"正常业务请求\"}".getBytes());
        req.setContentType("application/json");
        req.setAttribute("X-Tenant-Id", "t_1");
        req.setAttribute("X-User-Id", "u_1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("非 /api/v1/tasks 路径应跳过内容安全过滤直接放行")
    void should_SkipFilter_When_PathIsNotTaskEndpoint() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sessions/ss_001");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("消息端点 /api/v1/sessions/{id}/messages 内容命中违规词时应返回 400 CONTENT_BLOCKED")
    void should_BlockMessage_When_ContentViolatesAtMessageEndpoint() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/sessions/ss_001/messages");
        req.setContent("{\"content\":\"这是违规词测试\"}".getBytes());
        req.setContentType("application/json");
        req.setAttribute("X-Tenant-Id", "t_1");
        req.setAttribute("X-User-Id", "u_1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(resp.getHeader("X-Error-Code")).isEqualTo("CONTENT_BLOCKED");
    }
}
