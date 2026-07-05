package com.agent.tool.engine.gateway;

import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.exception.ToolEngineException;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.model.ToolMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP_API executor (doc 05-tool-engine §3.1).
 *
 * <p>POSTs {@code request.getInputJson()} to {@code meta.getEndpoint()} and
 * returns the response body as stdout. Uses {@link java.net.http.HttpClient}
 * (JDK 11+, no extra dependency).</p>
 *
 * <p>Failure modes:
 * <ul>
 *   <li>HTTP 4xx/5xx → status=FAILED, errorStack=status line</li>
 *   <li>Timeout → status=TIMEOUT</li>
 *   <li>IOException → status=FAILED, errorStack=exception message</li>
 * </ul>
 * </p>
 */
@Component
public class HttpExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpExecutor.class);

    private final HttpClient httpClient;

    public HttpExecutor() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Test constructor: inject a pre-configured HttpClient (for MockWebServer). */
    HttpExecutor(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ExecutorType type() {
        return ExecutorType.HTTP_API;
    }

    @Override
    public ToolCallResult execute(ToolMeta meta, ToolCallRequest request, long timeoutMs) {
        String endpoint = meta.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new ToolEngineException("TOOL_ENDPOINT_MISSING", 500,
                    "HTTP_API tool [" + meta.getToolId() + "] missing endpoint");
        }

        String body = request.getInputJson() != null ? request.getInputJson() : "{}";
        Duration timeout = timeoutMs > 0
                ? Duration.ofMillis(timeoutMs)
                : Duration.ofSeconds(30);

        long start = System.currentTimeMillis();
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            // Optional tenant header for downstream tracing.
            if (request.getTenantId() != null) {
                reqBuilder.header("X-Tenant-Id", request.getTenantId());
            }
            if (request.getTraceId() != null) {
                reqBuilder.header("X-Trace-Id", request.getTraceId());
            }

            HttpResponse<String> response = httpClient.send(
                    reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            long duration = System.currentTimeMillis() - start;
            int status = response.statusCode();
            String responseBody = response.body() != null ? response.body() : "";

            if (status >= 200 && status < 300) {
                log.debug("HTTP exec ok: tool={}, status={}, duration={}ms, bytes={}",
                        meta.getToolId(), status, duration, responseBody.length());
                ToolCallResult result = new ToolCallResult(
                        meta.getToolId(), responseBody, ToolCallStatus.SUCCESS);
                result.setOutputTokens(Math.max(1, responseBody.length() / 4));
                return result;
            }

            log.warn("HTTP exec non-2xx: tool={}, status={}, duration={}ms",
                    meta.getToolId(), status, duration);
            ToolCallResult failed = new ToolCallResult(
                    meta.getToolId(), responseBody, ToolCallStatus.FAILED);
            failed.setErrorStack("HTTP " + status + " " + response.headers().firstValue("reason").orElse(""));
            return failed;
        } catch (HttpTimeoutException e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("HTTP exec timeout: tool={}, duration={}ms", meta.getToolId(), duration);
            ToolCallResult timeoutResult = new ToolCallResult(
                    meta.getToolId(), "", ToolCallStatus.TIMEOUT);
            timeoutResult.setErrorStack("HTTP timeout after " + duration + "ms");
            return timeoutResult;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("HTTP exec failed: tool={}, duration={}ms, err={}",
                    meta.getToolId(), duration, e.getMessage());
            ToolCallResult failed = new ToolCallResult(
                    meta.getToolId(), "", ToolCallStatus.FAILED);
            failed.setErrorStack(e.getClass().getSimpleName() + ": " + e.getMessage());
            return failed;
        }
    }

    /** Convert a param map to URL query string (for GET-style tools, future use). */
    @SuppressWarnings("unused")
    private static String toQuery(Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) sb.append("&");
            sb.append(k).append("=").append(v);
        });
        return sb.toString();
    }
}
