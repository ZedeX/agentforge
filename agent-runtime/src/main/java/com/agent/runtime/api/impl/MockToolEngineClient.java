package com.agent.runtime.api.impl;

import com.agent.runtime.api.ToolEngineClient;
import com.agent.runtime.api.dto.ToolInvokeRequest;
import com.agent.runtime.api.dto.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Fallback {@link ToolEngineClient} for test/standalone environments (T5, doc 06-runtime §8.2).
 *
 * <p>Activated when {@code runtime.tool-engine-client.enabled=false} (or missing), which is the
 * default in {@code application-test.yml}. Returns deterministic mock responses so that
 * {@code RuntimeApplicationTest} context loads and {@code ActPhase} can run without a real gateway.
 *
 * <p>Mock behaviour:
 * <ul>
 *   <li>{@link #invoke(ToolInvokeRequest)}: returns success status with echoed inputJson as outputJson.</li>
 *   <li>{@link #invokeAsync(ToolInvokeRequest)}: completes immediately with the same mock result.</li>
 *   <li>{@link #invoke(String, String)}: returns the legacy mock JSON for ActPhase T3 compatibility.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "runtime.tool-engine-client.enabled",
        havingValue = "false", matchIfMissing = true)
public class MockToolEngineClient implements ToolEngineClient {

    private static final Logger log = LoggerFactory.getLogger(MockToolEngineClient.class);

    /** Legacy mock result string (kept stable for ActPhase T3 test expectations). */
    static final String LEGACY_MOCK_RESULT = "mock-result";

    @Override
    public ToolInvokeResult invoke(ToolInvokeRequest request) {
        log.debug("MockToolEngine invoke: callId={}, toolId={}",
                request.getCallId(), request.getToolId());
        String output = "{\"tool\":\"" + request.getToolId()
                + "\",\"status\":\"success\",\"result\":\"" + LEGACY_MOCK_RESULT + "\"}";
        return new ToolInvokeResult(
                request.getCallId(),
                ToolInvokeResult.STATUS_SUCCESS,
                output,
                "",
                "",
                1,
                0L,
                0,
                false);
    }

    @Override
    public CompletableFuture<ToolInvokeResult> invokeAsync(ToolInvokeRequest request) {
        return CompletableFuture.completedFuture(invoke(request));
    }

    @Override
    @Deprecated
    public String invoke(String toolId, String args) {
        log.info("调用工具引擎 (mock): toolId={}, args={}", toolId, args);
        return "{\"tool\":\"" + toolId + "\",\"status\":\"success\",\"result\":\"" + LEGACY_MOCK_RESULT + "\"}";
    }
}
