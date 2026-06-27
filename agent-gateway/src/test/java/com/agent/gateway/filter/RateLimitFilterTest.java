package com.agent.gateway.filter;

import com.agent.gateway.config.RateLimitConfig;
import com.agent.gateway.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void shouldAllow20ConsecutiveRequestsThenReject21st() throws Exception {
        String tenantId = "tenant-A";

        for (int i = 1; i <= 20; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
            req.addHeader("X-Tenant-Id", tenantId);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, resp, chain);
            assertEquals(HttpServletResponse.SC_OK, resp.getStatus(),
                    "第 " + i + " 次请求应通过");
        }

        MockHttpServletRequest req21 = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req21.addHeader("X-Tenant-Id", tenantId);
        MockHttpServletResponse resp21 = new MockHttpServletResponse();
        MockFilterChain chain21 = new MockFilterChain();
        filter.doFilter(req21, resp21, chain21);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), resp21.getStatus());
        assertEquals("RATE_LIMITED", resp21.getHeader("X-Error-Code"));
    }

    @Test
    void shouldIsolateBucketsByTenant() throws Exception {
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

        assertEquals(HttpServletResponse.SC_OK, respB.getStatus());
    }

    @Test
    void shouldFallbackToIpWhenTenantIdMissing() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new MockFilterChain());

        assertEquals(HttpServletResponse.SC_OK, resp.getStatus());
    }
}
