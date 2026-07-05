package com.agent.runtime.api.impl;

import agentplatform.tool.v1.ToolGatewayGrpc;
import agentplatform.tool.v1.ToolInvokeRequest;
import agentplatform.tool.v1.ToolInvokeResponse;
import com.agent.runtime.api.ToolEngineClient;
import com.agent.runtime.api.dto.ToolInvokeResult;
import com.agent.runtime.config.RuntimeProperties;
import com.agent.runtime.exception.ToolApprovalRequiredException;
import com.agent.runtime.exception.ToolEngineException;
import com.agent.runtime.exception.ToolExecutionTimeoutException;
import com.agent.runtime.exception.ToolNotFoundException;
import com.agent.runtime.exception.ToolQuotaExhaustedException;
import com.agent.common.exception.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Real tool engine client implementation backed by gRPC stubs (T5, doc 06-runtime §4).
 *
 * <p>Activated only when {@code runtime.tool-engine-client.enabled=true}. In test/standalone
 * environments where no real gateway is reachable, {@link MockToolEngineClient} takes over.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Map DTO {@link com.agent.runtime.api.dto.ToolInvokeRequest} → proto {@link ToolInvokeRequest} via {@link ToolCallMapper}.</li>
 *   <li>Call {@code ToolGatewayBlockingStub.invoke} with deadline = {@code request.timeoutMs} (default 60s).</li>
 *   <li>Async variant via {@link #invokeAsync(com.agent.runtime.api.dto.ToolInvokeRequest)} runs sync call on common pool.</li>
 *   <li>Translate gRPC {@link StatusRuntimeException} → typed {@link ToolEngineException} subclasses (T5 §12.4).</li>
 *   <li>Legacy {@link #invoke(String, String)} delegates to {@link #invoke(com.agent.runtime.api.dto.ToolInvokeRequest)}.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "runtime.tool-engine-client.enabled", havingValue = "true")
public class ToolEngineClientImpl implements ToolEngineClient {

    private static final Logger log = LoggerFactory.getLogger(ToolEngineClientImpl.class);

    private final ToolGatewayGrpc.ToolGatewayBlockingStub blockingStub;
    private final ToolCallMapper mapper;

    public ToolEngineClientImpl(ToolGatewayGrpc.ToolGatewayBlockingStub blockingStub,
                                RuntimeProperties properties) {
        this.blockingStub = blockingStub;
        this.mapper = new ToolCallMapper();
    }

    @Override
    public ToolInvokeResult invoke(com.agent.runtime.api.dto.ToolInvokeRequest request) {
        ToolInvokeRequest proto = mapper.toProto(request);
        log.debug("ToolEngine invoke: callId={}, toolId={}, agentId={}, timeoutMs={}",
                request.getCallId(), request.getToolId(), request.getAgentId(), request.getTimeoutMs());

        ToolInvokeResponse resp;
        try {
            resp = blockingStub.withDeadlineAfter(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
                    .invoke(proto);
        } catch (StatusRuntimeException ex) {
            throw translateException(ex, request.getCallId(), request.getToolId());
        }
        log.debug("ToolEngine invoke done: callId={}, status={}, durationMs={}, fromCache={}",
                resp.getCallId(), resp.getStatus(), resp.getDurationMs(), resp.getFromCache());
        return mapper.fromProto(resp);
    }

    @Override
    public CompletableFuture<ToolInvokeResult> invokeAsync(com.agent.runtime.api.dto.ToolInvokeRequest request) {
        return CompletableFuture.supplyAsync(() -> invoke(request));
    }

    @Override
    @Deprecated
    public String invoke(String toolId, String args) {
        com.agent.runtime.api.dto.ToolInvokeRequest req =
                com.agent.runtime.api.dto.ToolInvokeRequest.builder()
                .callId("legacy-" + System.nanoTime())
                .toolId(toolId)
                .inputJson(args == null ? "" : args)
                .timeoutMs(60_000L)
                .build();
        ToolInvokeResult result = invoke(req);
        return result.getOutputJson();
    }

    /** Translate gRPC status → typed tool engine exception (T5 §12.4 error mapping). */
    private RuntimeException translateException(StatusRuntimeException ex,
                                               String callId, String toolId) {
        Status.Code code = ex.getStatus().getCode();
        String desc = ex.getStatus().getDescription();
        log.warn("ToolEngine RPC failed: callId={}, toolId={}, code={}, desc={}",
                callId, toolId, code, desc);

        String ctx = "callId=" + callId + ", toolId=" + toolId;
        switch (code) {
            case NOT_FOUND:
                return new ToolNotFoundException("tool not found: " + ctx, ex);
            case PERMISSION_DENIED:
                // Approval flow: server may embed approval_call_id in description
                String approvalCallId = extractApprovalCallId(desc);
                return new ToolApprovalRequiredException(
                        "tool approval required: " + ctx, approvalCallId, ex);
            case RESOURCE_EXHAUSTED:
                return new ToolQuotaExhaustedException("tool quota exhausted: " + ctx, ex);
            case DEADLINE_EXCEEDED:
                return new ToolExecutionTimeoutException("tool execution timeout: " + ctx, ex);
            default:
                return new ToolEngineException(ErrorCode.INTERNAL,
                        "tool engine error: code=" + code + ", " + ctx, ex);
        }
    }

    /** Extract approval_call_id from gRPC error description if present (format: "approval:apc_xxx"). */
    private String extractApprovalCallId(String desc) {
        if (desc == null || desc.isEmpty()) return null;
        int idx = desc.indexOf("approval:");
        if (idx < 0) return null;
        return desc.substring(idx + "approval:".length()).trim();
    }
}
