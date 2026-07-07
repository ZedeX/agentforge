package com.agent.gateway.filter;

import com.agent.gateway.config.ApiKeyProperties;
import com.agent.gateway.config.JwtProperties;
import com.agent.gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFilterTest {

    private JwtUtil jwtUtil;
    private AuthFilter.Whitelist whitelist;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-jwt-secret-key-at-least-32-bytes-long-for-hmac-sha256!");
        props.setIssuer("agent-platform");
        props.setTtlMinutes(60);
        jwtUtil = new JwtUtil(props);
        whitelist = new AuthFilter.Whitelist();
        whitelist.setPaths(List.of("/api/v1/health", "POST /api/v1/sessions"));
    }

    // ---- JWT path tests ----

    @Test
    @DisplayName("缺少 Authorization 头时应返回 401 UNAUTHENTICATED 且中断过滤器链")
    void should_Return401_When_AuthorizationHeaderMissing() throws Exception {
        AuthFilter filter = new AuthFilter(jwtUtil, whitelist);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(resp.getHeader("X-Error-Code")).isEqualTo("UNAUTHENTICATED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("携带有效 JWT 时应返回 200 并注入 X-User-Id/X-Tenant-Id 到请求属性")
    void should_Return200_When_JwtValid() throws Exception {
        AuthFilter filter = new AuthFilter(jwtUtil, whitelist);
        String token = jwtUtil.generate("u_1001", "t_1", List.of("ROLE_USER"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest().getAttribute("X-User-Id")).isEqualTo("u_1001");
        assertThat(chain.getRequest().getAttribute("X-Tenant-Id")).isEqualTo("t_1");
    }

    // ---- Whitelist tests ----

    @Test
    @DisplayName("GET /api/v1/health 在白名单中应无需鉴权直接放行")
    void should_PassWithoutAuth_When_PathInWhitelist() throws Exception {
        AuthFilter filter = new AuthFilter(jwtUtil, whitelist);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("POST /api/v1/sessions 在白名单中应无需鉴权直接放行")
    void should_PassWithoutAuth_When_PostSessionsInWhitelist() throws Exception {
        AuthFilter filter = new AuthFilter(jwtUtil, whitelist);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/sessions");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    // ---- API-Key security hardening tests (R-01, R-12, R-13) ----

    @Nested
    @DisplayName("R-01: 硬编码 API Key 后门已移除")
    class HardcodedApiKeyRemoved {

        @Test
        @DisplayName("旧硬编码 API Key ak_test_valid_key_2026 应被拒绝")
        void shouldRejectOldHardcodedApiKey() throws Exception {
            // 默认 ApiKeyProperties = empty validKeys → any key rejected
            AuthFilter filter = new AuthFilter(jwtUtil, whitelist);
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
            req.addHeader("X-API-Key", "ak_test_valid_key_2026");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertThat(resp.getHeader("X-Error-Code")).isEqualTo("UNAUTHENTICATED");
        }

        @Test
        @DisplayName("未配置 validKeys 时任何 API Key 都应被拒绝")
        void shouldRejectAnyKey_When_ValidKeysEmpty() throws Exception {
            AuthFilter filter = new AuthFilter(jwtUtil, whitelist, new ApiKeyProperties());
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
            req.addHeader("X-API-Key", "some-random-key");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("R-01 + R-13: 配置化 API Key + 租户绑定")
    class ConfiguredApiKeyWithTenantBinding {

        private ApiKeyProperties apiKeyProperties;

        @BeforeEach
        void setUpKeys() {
            apiKeyProperties = new ApiKeyProperties();
            apiKeyProperties.setValidKeys(List.of("ak_prod_key_001", "ak_prod_key_002"));
            apiKeyProperties.setKeyToTenantId(Map.of(
                "ak_prod_key_001", "tenant-A",
                "ak_prod_key_002", "tenant-B"
            ));
        }

        @Test
        @DisplayName("配置中的有效 API Key 应通过鉴权")
        void shouldAcceptConfiguredApiKey() throws Exception {
            AuthFilter filter = new AuthFilter(jwtUtil, whitelist, apiKeyProperties);
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
            req.addHeader("X-API-Key", "ak_prod_key_001");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(chain.getRequest().getAttribute("X-User-Id")).isEqualTo("system");
        }

        @Test
        @DisplayName("R-13: API Key 绑定的 tenantId 应从映射取，不信任客户端 X-Tenant-Id header")
        void shouldUseBoundTenantId_NotClientHeader() throws Exception {
            AuthFilter filter = new AuthFilter(jwtUtil, whitelist, apiKeyProperties);
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
            req.addHeader("X-API-Key", "ak_prod_key_001");
            req.addHeader("X-Tenant-Id", "tenant-EVIL"); // 试图跨租户
            MockHttpServletResponse resp = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            // tenantId 必须是绑定的 tenant-A，不是客户端填的 tenant-EVIL
            assertThat(chain.getRequest().getAttribute("X-Tenant-Id")).isEqualTo("tenant-A");
        }

        @Test
        @DisplayName("API Key 无租户绑定时应使用 default 租户")
        void shouldUseDefaultTenant_When_NoBinding() throws Exception {
            ApiKeyProperties props = new ApiKeyProperties();
            props.setValidKeys(List.of("ak_no_binding"));
            // keyToTenantId 为空 → fallback to "default"
            AuthFilter filter = new AuthFilter(jwtUtil, whitelist, props);
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
            req.addHeader("X-API-Key", "ak_no_binding");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(chain.getRequest().getAttribute("X-Tenant-Id")).isEqualTo("default");
        }

        @Test
        @DisplayName("不在 validKeys 中的 API Key 应被拒绝")
        void shouldRejectUnknownApiKey() throws Exception {
            AuthFilter filter = new AuthFilter(jwtUtil, whitelist, apiKeyProperties);
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
            req.addHeader("X-API-Key", "ak_unknown_key");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, resp, chain);

            assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("R-12: isInternalGrpcCall 方法已移除")
    class InternalGrpcMethodRemoved {

        @Test
        @DisplayName("AuthFilter 不应再有 isInternalGrpcCall 方法")
        void shouldNotHaveIsInternalGrpcCallMethod() throws NoSuchMethodException {
            // 验证方法已删除
            boolean hasMethod;
            try {
                AuthFilter.class.getMethod("isInternalGrpcCall", HttpServletRequest.class);
                hasMethod = true;
            } catch (NoSuchMethodException e) {
                hasMethod = false;
            }
            assertThat(hasMethod)
                .as("isInternalGrpcCall should be removed (R-12: client-forgable header trust)")
                .isFalse();
        }
    }
}
