package com.agent.gateway.filter;

import com.agent.gateway.config.RateLimitConfig;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 限流过滤器：
 *  - 维度：优先使用 X-Tenant-Id，缺失时使用客户端 IP
 *  - 策略：每秒 10 token、突发 20（与 application.yml 一致）
 *  - 触发限流返回 429 RATE_LIMITED
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitConfig rateLimitConfig;

    public RateLimitFilter(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = resolveKey(request);
        Bucket bucket = rateLimitConfig.getBucket(key);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("限流触发 key={} path={}", key, request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("X-Error-Code", "RATE_LIMITED");
        response.setHeader("Retry-After", "1");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁\"}");
    }

    private String resolveKey(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-Id");
        if (StringUtils.hasText(tenantId)) {
            return "tenant:" + tenantId;
        }
        String ip = request.getRemoteAddr();
        return "ip:" + (ip != null ? ip : "unknown");
    }
}
