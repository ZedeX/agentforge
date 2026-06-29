package com.agent.gateway.filter;

import com.agent.gateway.config.JwtProperties;
import com.agent.gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("缺少 Authorization 头时应返回 401 UNAUTHENTICATED 且中断过滤器链")
    void should_Return401_When_AuthorizationHeaderMissing() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(resp.getHeader("X-Error-Code")).isEqualTo("UNAUTHENTICATED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("携带有效 JWT 时应返回 200 并注入 X-User-Id/X-Tenant-Id 到请求属性")
    void should_Return200_When_JwtValid() throws Exception {
        String token = jwtUtil.generate("u_1001", "t_1", List.of("ROLE_USER"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
        req.addHeader("Authorization", "Bearer " + token);
        req.addHeader("X-Tenant-Id", "t_1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest().getAttribute("X-User-Id")).isEqualTo("u_1001");
        assertThat(chain.getRequest().getAttribute("X-Tenant-Id")).isEqualTo("t_1");
    }

    @Test
    @DisplayName("携带有效 X-API-Key 时应返回 200 并以 system 用户身份放行")
    void should_Return200_When_ApiKeyValid() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
        req.addHeader("X-API-Key", "ak_test_valid_key_2026");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest().getAttribute("X-User-Id")).isEqualTo("system");
    }

    @Test
    @DisplayName("GET /api/v1/health 在白名单中应无需鉴权直接放行")
    void should_PassWithoutAuth_When_PathInWhitelist() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("POST /api/v1/sessions 在白名单中应无需鉴权直接放行")
    void should_PassWithoutAuth_When_PostSessionsInWhitelist() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/sessions");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    // UT-F1-001: 内部 gRPC 调用判定（用于跳过 JWT 走 mTLS 链路）
    @Test
    @DisplayName("携带 X-Internal-Source=grpc 头时应识别为内部 gRPC 调用")
    void should_IdentifyInternalGrpc_When_HeaderPresent() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req.addHeader("X-Internal-Source", "grpc");

        boolean result = authFilter.isInternalGrpcCall(req);

        assertThat(result).as("携带 X-Internal-Source=grpc 头时应识别为内部 gRPC 调用").isTrue();
    }

    @Test
    @DisplayName("缺少 X-Internal-Source 头时应识别为非内部 gRPC 调用")
    void should_NotIdentifyInternalGrpc_When_HeaderMissing() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");

        boolean result = authFilter.isInternalGrpcCall(req);

        assertThat(result).as("无内部来源头时应识别为非内部 gRPC 调用").isFalse();
    }

    @Test
    @DisplayName("X-Internal-Source 非 grpc 值时应识别为非内部 gRPC 调用")
    void should_NotIdentifyInternalGrpc_When_HeaderNotGrpc() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req.addHeader("X-Internal-Source", "rest");

        boolean result = authFilter.isInternalGrpcCall(req);

        assertThat(result).as("X-Internal-Source 非 grpc 值时应识别为非内部 gRPC 调用").isFalse();
    }
}
