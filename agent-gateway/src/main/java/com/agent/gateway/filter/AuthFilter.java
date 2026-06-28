package com.agent.gateway.filter;

import com.agent.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 鉴权过滤器：
 *  - 校验 Authorization: Bearer <jwt> 或 X-API-Key
 *  - 白名单路径直接放行（如 GET /api/v1/health、POST /api/v1/sessions）
 *  - 校验通过后将 userId/tenantId 注入请求属性供下游使用
 */
public class AuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String VALID_API_KEY = "ak_test_valid_key_2026";
    private static final String INTERNAL_GRPC_HEADER = "X-Internal-Source";
    private static final String INTERNAL_GRPC_VALUE = "grpc";

    private final JwtUtil jwtUtil;
    private final Whitelist whitelist;

    public AuthFilter(JwtUtil jwtUtil, Whitelist whitelist) {
        this.jwtUtil = jwtUtil;
        this.whitelist = whitelist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (isWhitelisted(method, path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        String apiKey = request.getHeader("X-API-Key");

        String userId = null;
        String tenantId = null;

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtUtil.parseAndValidate(token);
                userId = claims.getSubject();
                tenantId = claims.get("tenantId", String.class);
            } catch (Exception e) {
                log.warn("JWT 校验失败 path={} reason={}", path, e.getMessage());
                reject(response);
                return;
            }
        } else if (StringUtils.hasText(apiKey)) {
            if (!VALID_API_KEY.equals(apiKey)) {
                log.warn("API-Key 无效 path={}", path);
                reject(response);
                return;
            }
            userId = "system";
            tenantId = request.getHeader("X-Tenant-Id");
            if (!StringUtils.hasText(tenantId)) {
                tenantId = "0";
            }
        } else {
            log.warn("缺少鉴权凭证 path={}", path);
            reject(response);
            return;
        }

        request.setAttribute("X-User-Id", userId);
        request.setAttribute("X-Tenant-Id", tenantId);
        filterChain.doFilter(request, response);
    }

    /**
     * UT-F1-001: 判定请求是否来自内部 gRPC 链路。
     *
     * <p>判定规则：HTTP 请求头 {@code X-Internal-Source} 值为 {@code grpc}
     * （大小写不敏感）时识别为内部 gRPC 调用。</p>
     *
     * <p>用途：当 gRPC 端口无法被 Servlet Filter 直接感知时（如通过 Envoy/gRPC-Web
     * 网关桥接的 HTTP 请求），AuthFilter 据此跳过 JWT/API-Key 校验，
     * 由上游 mTLS 终结身份认证；同时 TaskCreateRequest 会标记
     * {@code internal=true}（由 ProtocolAdapter 设置）。</p>
     *
     * <p>测试用 mock：直接注入 header 验证判定逻辑，不依赖真实 mTLS 握手。</p>
     *
     * @param request HTTP 请求
     * @return true 表示内部 gRPC 链路；false 表示普通 REST 调用
     */
    public boolean isInternalGrpcCall(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String header = request.getHeader(INTERNAL_GRPC_HEADER);
        return INTERNAL_GRPC_VALUE.equalsIgnoreCase(header);
    }

    private boolean isWhitelisted(String method, String path) {
        if (whitelist == null || whitelist.getPaths() == null) {
            return false;
        }
        for (String rule : whitelist.getPaths()) {
            String trimmed = rule.trim();
            if (!trimmed.contains(" ")) {
                if (trimmed.equals(path)) {
                    return true;
                }
            } else {
                String[] parts = trimmed.split("\\s+", 2);
                String ruleMethod = parts[0].toUpperCase();
                String rulePath = parts[1];
                if (ruleMethod.equalsIgnoreCase(method) && rulePath.equals(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader("X-Error-Code", "UNAUTHENTICATED");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"鉴权失败\"}");
    }

    @ConfigurationProperties(prefix = "gateway.auth-whitelist")
    public static class Whitelist {
        private List<String> paths;

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths;
        }
    }
}
