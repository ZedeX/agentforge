package com.agent.gateway.filter;

import com.agent.gateway.client.RiskControlClient;
import com.agent.gateway.dto.SafetyCheckResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 内容安全过滤器：
 *  - 仅拦截 POST /api/v1/tasks 与 POST /api/v1/sessions/{id}/messages
 *  - 提取请求体中的 goal / content 字段，调用 risk-control.preCheck
 *  - 命中违规返回 400 CONTENT_BLOCKED
 */
public class ContentSafetyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ContentSafetyFilter.class);
    private static final Pattern TASK_PATTERN = Pattern.compile("^/api/v1/tasks/?$");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("^/api/v1/sessions/[^/]+/messages/?$");

    private final RiskControlClient riskControlClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContentSafetyFilter(RiskControlClient riskControlClient) {
        this.riskControlClient = riskControlClient;
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

        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        wrapped.getInputStream().readAllBytes();
        String body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
        String content = extractContent(body);

        String tenantId = (String) request.getAttribute("X-Tenant-Id");
        String userId = (String) request.getAttribute("X-User-Id");

        SafetyCheckResult result = riskControlClient.preCheck(tenantId, userId, content);
        if (result.isBlocked()) {
            log.warn("内容安全拦截 path={} reason={}", path, result.reason());
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setHeader("X-Error-Code", "CONTENT_BLOCKED");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            response.getWriter().write("{\"code\":\"CONTENT_BLOCKED\",\"message\":\"内容安全拦截\",\"details\":{\"reason\":\""
                    + escape(result.reason()) + "\"}}");
            return;
        }

        filterChain.doFilter(wrapped, response);
    }

    private String extractContent(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("goal")) {
                return node.get("goal").asText();
            }
            if (node.has("content")) {
                return node.get("content").asText();
            }
            return body;
        } catch (Exception e) {
            return body;
        }
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
