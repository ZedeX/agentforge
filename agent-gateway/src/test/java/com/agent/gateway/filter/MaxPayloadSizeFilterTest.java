package com.agent.gateway.filter;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.gateway.config.MaxPayloadSizeProperties;
import com.agent.gateway.service.AuditLogService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * UT-F1-002: MaxPayloadSizeFilter 单元测试。
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>请求体超过 1MB 拒绝（413 PAYLOAD_TOO_LARGE）</li>
 *   <li>请求体在阈值内放行</li>
 *   <li>拒绝时调用 AuditLogService.record 留痕</li>
 * </ul>
 */
class MaxPayloadSizeFilterTest {

    private static final int ONE_MB = 1024 * 1024;

    private MaxPayloadSizeProperties properties;
    private AuditLogService auditLogService;
    private MaxPayloadSizeFilter filter;

    @BeforeEach
    void setUp() {
        properties = new MaxPayloadSizeProperties();
        properties.setMaxSize(ONE_MB);
        auditLogService = mock(AuditLogService.class);
        filter = new MaxPayloadSizeFilter(properties, auditLogService);
    }

    /**
     * UT-F1-002 主用例：2MB body 应抛 PAYLOAD_TOO_LARGE (httpStatus=413)。
     */
    @Test
    void should_RejectWith413_When_BodyExceeds1MB() throws Exception {
        byte[] oversizedBody = new byte[2 * ONE_MB];

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req.setContent(oversizedBody);
        req.setContentType("application/json");
        req.setAttribute("X-Tenant-Id", "t_demo");
        req.setAttribute("X-User-Id", "u_demo");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> filter.doFilter(req, resp, chain));

        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, ex.getErrorCode());
        assertEquals(413, ex.getErrorCode().getHttpStatus(),
                "PAYLOAD_TOO_LARGE 必须 mapping 到 HTTP 413");
        assertEquals("PAYLOAD_TOO_LARGE", ex.getErrorCode().getCode());
    }

    /**
     * 边界用例：512KB body 在 1MB 阈值内，应放行。
     */
    @Test
    void should_AllowRequest_When_BodyWithinLimit() throws Exception {
        byte[] normalBody = new byte[512 * 1024];

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tasks");
        req.setContent(normalBody);
        req.setContentType("application/json");
        req.setAttribute("X-Tenant-Id", "t_demo");
        req.setAttribute("X-User-Id", "u_demo");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertEquals(HttpServletResponse.SC_OK, resp.getStatus());
        assertNotNull(chain.getRequest(), "filterChain.doFilter 应被调用，请求应放行");
        verifyNoInteractions(auditLogService);
    }

    /**
     * 审计用例：拒绝时调用 AuditLogService.record 留痕，记录 errorCode=PAYLOAD_TOO_LARGE。
     */
    @Test
    void should_RecordAuditLog_When_PayloadRejected() throws Exception {
        byte[] oversizedBody = new byte[2 * ONE_MB];

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/sessions/ss_001/messages");
        req.setContent(oversizedBody);
        req.setContentType("application/json");
        req.setAttribute("X-Tenant-Id", "t_audit");
        req.setAttribute("X-User-Id", "u_audit");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // filter 内部会先调用 auditLogService.record，再抛 BusinessException
        assertThrows(BusinessException.class, () -> filter.doFilter(req, resp, chain));

        ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> errorCodeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);

        verify(auditLogService).record(
                tenantCaptor.capture(),
                userCaptor.capture(),
                actionCaptor.capture(),
                errorCodeCaptor.capture(),
                detailCaptor.capture());

        assertEquals("t_audit", tenantCaptor.getValue());
        assertEquals("u_audit", userCaptor.getValue());
        assertEquals("PAYLOAD_REJECTED", actionCaptor.getValue());
        assertEquals("PAYLOAD_TOO_LARGE", errorCodeCaptor.getValue());
        assertTrue(detailCaptor.getValue().contains("/api/v1/sessions/ss_001/messages"),
                "审计详情应包含被拒路径");
    }
}
