package com.agent.gateway.filter;

import com.agent.gateway.config.JwtProperties;
import com.agent.gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthFilterTest {

    private JwtUtil jwtUtil;
    private AuthFilter authFilter;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("agent-platform-jwt-secret-key-please-change-in-production-32bytes");
        props.setIssuer("agent-platform");
        props.setTtlMinutes(60);
        jwtUtil = new JwtUtil(props);
        AuthFilter.Whitelist whitelist = new AuthFilter.Whitelist();
        whitelist.setPaths(List.of("/api/v1/health", "POST /api/v1/sessions"));
        authFilter = new AuthFilter(jwtUtil, whitelist);
    }

    @Test
    void shouldReturn401WhenAuthorizationHeaderMissing() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(req, resp, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, resp.getStatus());
        assertEquals("UNAUTHENTICATED", resp.getHeader("X-Error-Code"));
        assertNull(chain.getRequest());
    }

    @Test
    void shouldReturn200WhenJwtValid() throws Exception {
        String token = jwtUtil.generate("u_1001", "t_1", List.of("ROLE_USER"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
        req.addHeader("Authorization", "Bearer " + token);
        req.addHeader("X-Tenant-Id", "t_1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(req, resp, chain);

        assertEquals(HttpServletResponse.SC_OK, resp.getStatus());
        assertEquals("u_1001", chain.getRequest().getAttribute("X-User-Id"));
        assertEquals("t_1", chain.getRequest().getAttribute("X-Tenant-Id"));
    }

    @Test
    void shouldReturn200WhenApiKeyValid() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
        req.addHeader("X-API-Key", "ak_test_valid_key_2026");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(req, resp, chain);

        assertEquals(HttpServletResponse.SC_OK, resp.getStatus());
        assertEquals("system", chain.getRequest().getAttribute("X-User-Id"));
    }

    @Test
    void shouldPassWhitelistWithoutAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(req, resp, chain);

        assertEquals(HttpServletResponse.SC_OK, resp.getStatus());
    }

    @Test
    void shouldPassWhitelistForPostSessions() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/sessions");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(req, resp, chain);

        assertEquals(HttpServletResponse.SC_OK, resp.getStatus());
    }

    // UT-F1-001: 内部 gRPC 调用判定（用于跳过 JWT 走 mTLS 链路）
    @Test
    void shouldIdentifyInternalGrpcCallWhenHeaderPresent() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req.addHeader("X-Internal-Source", "grpc");

        boolean result = authFilter.isInternalGrpcCall(req);

        assertTrue(result, "携带 X-Internal-Source=grpc 头时应识别为内部 gRPC 调用");
    }

    @Test
    void shouldNotIdentifyInternalGrpcCallWhenHeaderMissing() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");

        boolean result = authFilter.isInternalGrpcCall(req);

        assertFalse(result, "无内部来源头时应识别为非内部 gRPC 调用");
    }

    @Test
    void shouldNotIdentifyInternalGrpcCallWhenHeaderNotGrpc() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req.addHeader("X-Internal-Source", "rest");

        boolean result = authFilter.isInternalGrpcCall(req);

        assertFalse(result, "X-Internal-Source 非 grpc 值时应识别为非内部 gRPC 调用");
    }
}
