package com.agent.gateway.filter;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.gateway.config.MaxPayloadSizeProperties;
import com.agent.gateway.service.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 请求体大小限制过滤器：
 *  - 仅拦截 POST /api/v1/tasks 和 POST /api/v1/sessions/{id}/messages
 *  - 通过 Content-Length 头或 getContentAsByteArray().length 判断
 *  - 超阈值时调用 AuditLogService 记录审计并抛 BusinessException(PAYLOAD_TOO_LARGE)
 *
 * <p>过滤顺序建议：在 AuthFilter 之后、ContentSafetyFilter 之前。</p>
 */
public class MaxPayloadSizeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MaxPayloadSizeFilter.class);
    private static final Pattern TASK_PATTERN = Pattern.compile("^/api/v1/tasks/?$");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("^/api/v1/sessions/[^/]+/messages/?$");

    private final MaxPayloadSizeProperties properties;
    private final AuditLogService auditLogService;

    public MaxPayloadSizeFilter(MaxPayloadSizeProperties properties, AuditLogService auditLogService) {
        this.properties = properties;
        this.auditLogService = auditLogService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (!"POST".equalsIgnoreCase(method)
                || (!TASK_PATTERN.matcher(path).matches() && !MESSAGE_PATTERN.matcher(path).matches())) {
            filterChain.doFilter(request, response);
            return;
        }

        int maxSize = properties.getMaxSize();
        int contentLength = resolveContentLength(request);

        if (contentLength > maxSize) {
            String tenantId = (String) request.getAttribute("X-Tenant-Id");
            String userId = (String) request.getAttribute("X-User-Id");
            String detail = String.format("path=%s bodySize=%d maxSize=%d", path, contentLength, maxSize);

            log.warn("Payload 超限拒绝 path={} bodySize={} maxSize={}", path, contentLength, maxSize);
            auditLogService.record(tenantId, userId, "PAYLOAD_REJECTED",
                    ErrorCode.PAYLOAD_TOO_LARGE.getCode(), detail);

            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "请求体过大: " + contentLength + " bytes (limit=" + maxSize + ")");
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 优先使用 Content-Length 头；若为 -1（chunked 或缺失），回退到实际读取 body。
     */
    private int resolveContentLength(HttpServletRequest request) throws IOException {
        int contentLength = request.getContentLength();
        if (contentLength >= 0) {
            return contentLength;
        }
        // 兜底：实际读取 body
        byte[] body = request.getInputStream().readAllBytes();
        return body.length;
    }
}
