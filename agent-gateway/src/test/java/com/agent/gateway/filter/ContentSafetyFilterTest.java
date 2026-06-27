package com.agent.gateway.filter;

import com.agent.gateway.client.RiskControlClient;
import com.agent.gateway.dto.SafetyCheckResult;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void shouldBlockWhenContentViolates() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req.setContent("{\"goal\":\"包含违规词的内容\"}".getBytes());
        req.setContentType("application/json");
        req.setAttribute("X-Tenant-Id", "t_1");
        req.setAttribute("X-User-Id", "u_1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, resp.getStatus());
        assertEquals("CONTENT_BLOCKED", resp.getHeader("X-Error-Code"));
        assertNull(chain.getRequest());
    }

    @Test
    void shouldPassWhenContentClean() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req.setContent("{\"goal\":\"正常业务请求\"}".getBytes());
        req.setContentType("application/json");
        req.setAttribute("X-Tenant-Id", "t_1");
        req.setAttribute("X-User-Id", "u_1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertEquals(HttpServletResponse.SC_OK, resp.getStatus());
    }

    @Test
    void shouldSkipNonTaskPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sessions/ss_001");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertEquals(HttpServletResponse.SC_OK, resp.getStatus());
    }

    @Test
    void shouldHandleMessageEndpoint() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/sessions/ss_001/messages");
        req.setContent("{\"content\":\"这是违规词测试\"}".getBytes());
        req.setContentType("application/json");
        req.setAttribute("X-Tenant-Id", "t_1");
        req.setAttribute("X-User-Id", "u_1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, resp.getStatus());
        assertEquals("CONTENT_BLOCKED", resp.getHeader("X-Error-Code"));
    }
}
