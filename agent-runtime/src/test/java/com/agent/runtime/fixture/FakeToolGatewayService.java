package com.agent.runtime.fixture;

import agentplatform.tool.v1.ToolGatewayGrpc;
import agentplatform.tool.v1.ToolInvokeRequest;
import agentplatform.tool.v1.ToolInvokeResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.atomic.AtomicReference;

/**
 * T5 in-process gRPC fixture: fake {@link ToolGatewayGrpc.ToolGatewayImplBase}.
 *
 * <p>Routes responses based on {@code request.callId} prefix so test cases can assert
 * deterministic outcomes without external state:
 * <ul>
 *   <li>{@code notfound_*} → {@link Status#NOT_FOUND} error.</li>
 *   <li>{@code approval_*} → {@link Status#PERMISSION_DENIED} with desc {@code approval:apc_xxx}.</li>
 *   <li>{@code quota_*} → {@link Status#RESOURCE_EXHAUSTED}.</li>
 *   <li>{@code timeout_*} → {@link Status#DEADLINE_EXCEEDED}.</li>
 *   <li>{@code cleaned_*} → response whose outputJson contains no PII (pre-cleaned).</li>
 *   <li>{@code audit_*} → response with callId + fromCache=true for audit assertions.</li>
 *   <li>default → success response mirroring inputJson as outputJson.</li>
 * </ul>
 *
 * <p>The most recent request is captured into {@link #lastRequest} so tests can verify
 * that the client sent the expected agentId / taskId / callId.
 */
public class FakeToolGatewayService extends ToolGatewayGrpc.ToolGatewayImplBase {

    private final AtomicReference<ToolInvokeRequest> lastRequest = new AtomicReference<>();

    public ToolInvokeRequest getLastRequest() {
        return lastRequest.get();
    }

    @Override
    public void invoke(ToolInvokeRequest request, StreamObserver<ToolInvokeResponse> responseObserver) {
        lastRequest.set(request);
        String callId = request.getCallId();

        try {
            if (callId == null) {
                responseObserver.onError(Status.INTERNAL
                        .withDescription("missing callId").asRuntimeException());
                return;
            }

            if (callId.startsWith("notfound_")) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("tool not found").asRuntimeException());
                return;
            }
            if (callId.startsWith("approval_")) {
                responseObserver.onError(Status.PERMISSION_DENIED
                        .withDescription("approval:apc_test_001").asRuntimeException());
                return;
            }
            if (callId.startsWith("quota_")) {
                responseObserver.onError(Status.RESOURCE_EXHAUSTED
                        .withDescription("quota exhausted").asRuntimeException());
                return;
            }
            if (callId.startsWith("timeout_")) {
                responseObserver.onError(Status.DEADLINE_EXCEEDED
                        .withDescription("tool timeout").asRuntimeException());
                return;
            }

            ToolInvokeResponse.Builder resp = ToolInvokeResponse.newBuilder()
                    .setCallId(callId)
                    .setStatus("success")
                    .setDurationMs(50)
                    .setCostCent(0L)
                    .setTokenUsed(0)
                    .setFromCache(false);

            if (callId.startsWith("cleaned_")) {
                // Pre-cleaned output: no PII patterns (no phone numbers, no emails)
                resp.setOutputJson("{\"result\":\"cleaned-content-no-pii\"}");
            } else if (callId.startsWith("audit_")) {
                resp.setOutputJson("{\"result\":\"with-audit\"}");
                resp.setFromCache(true);
            } else {
                // Default: echo inputJson as outputJson
                resp.setOutputJson(request.getInputJson());
            }

            responseObserver.onNext(resp.build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(t);
        }
    }
}
