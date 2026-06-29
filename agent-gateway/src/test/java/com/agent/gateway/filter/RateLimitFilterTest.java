package com.agent.gateway.filter;

import com.agent.gateway.config.RateLimitConfig;
import com.agent.gateway.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties();
        props.setCapacity(20);
        props.setRefillTokens(10);
        props.setRefillSeconds(1);
        RateLimitConfig config = new RateLimitConfig(props);
        filter = new RateLimitFilter(config);
    }

    @Test
    @DisplayName("令牌桶容量 20 时连续 20 次请求应放行，第 21 次应返回 429 RATE_LIMITED")
    void should_Allow20ThenReject21st_When_TokenBucketExhausted() throws Exception {
        String tenantId = "tenant-A";

        for (int i = 1; i <= 20; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
            req.addHeader("X-Tenant-Id", tenantId);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, resp, chain);
            assertThat(resp.getStatus())
                    .as("第 " + i + " 次请求应通过")
                    .isEqualTo(HttpServletResponse.SC_OK);
        }

        MockHttpServletRequest req21 = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req21.addHeader("X-Tenant-Id", tenantId);
        MockHttpServletResponse resp21 = new MockHttpServletResponse();
        MockFilterChain chain21 = new MockFilterChain();
        filter.doFilter(req21, resp21, chain21);

        assertThat(resp21.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(resp21.getHeader("X-Error-Code")).isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("不同租户的令牌桶应隔离，tenant-A 耗尽不应影响 tenant-B")
    void should_IsolateBuckets_When_DifferentTenants() throws Exception {
        for (int i = 1; i <= 20; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
            req.addHeader("X-Tenant-Id", "tenant-A");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(req, resp, new MockFilterChain());
        }

        MockHttpServletRequest reqB = new MockHttpServletRequest("POST", "/api/v1/tasks");
        reqB.addHeader("X-Tenant-Id", "tenant-B");
        MockHttpServletResponse respB = new MockHttpServletResponse();
        filter.doFilter(reqB, respB, new MockFilterChain());

        assertThat(respB.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("缺少 X-Tenant-Id 头时应回退到客户端 IP 作为限流 key")
    void should_FallbackToIp_When_TenantIdMissing() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
