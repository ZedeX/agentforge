package com.agent.runtime.fixture;

import agentplatform.model.v1.ChatChunk;
import agentplatform.model.v1.ChatRequest;
import agentplatform.model.v1.ChatResponse;
import agentplatform.model.v1.FinishReason;
import agentplatform.model.v1.ModelGatewayGrpc;
import agentplatform.model.v1.ToolCall;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.atomic.AtomicReference;

/**
 * T4 in-process gRPC fixture: fake {@link ModelGatewayGrpc.ModelGatewayImplBase}.
 *
 * <p>Routes responses based on {@code request.callId} prefix so test cases can assert
 * deterministic outcomes without external state:
 * <ul>
 *   <li>{@code unavailable_*} → {@link Status#UNAVAILABLE} error.</li>
 *   <li>{@code timeout_*} → {@link Status#DEADLINE_EXCEEDED} error.</li>
 *   <li>{@code toolcall_*} → response with one {@link ToolCall} (toolId=t0, inputJson={"q":"x"}).</li>
 *   <li>{@code final_*} → response whose content starts with {@code final_answer:}.</li>
 *   <li>{@code tokens_*} → response with inputTokens=100, outputTokens=50.</li>
 *   <li>{@code stream_*} → emits 3 chunks (delta1, delta2, STOP) via {@code StreamChat}.</li>
 *   <li>default → simple text response whose content mirrors last user message.</li>
 * </ul>
 *
 * <p>The most recent request is captured into {@link #lastRequest} so tests can verify
 * that the client sent the expected messages / params.
 */
public class FakeModelGatewayService extends ModelGatewayGrpc.ModelGatewayImplBase {

    private final AtomicReference<ChatRequest> lastRequest = new AtomicReference<>();

    public ChatRequest getLastRequest() {
        return lastRequest.get();
    }

    @Override
    public void chat(ChatRequest request, StreamObserver<ChatResponse> responseObserver) {
        lastRequest.set(request);
        String callId = request.getCallId();

        try {
            if (callId == null) {
                responseObserver.onError(Status.INTERNAL
                        .withDescription("missing callId").asRuntimeException());
                return;
            }

            if (callId.startsWith("unavailable_")) {
                responseObserver.onError(Status.UNAVAILABLE
                        .withDescription("fake unavailable").asRuntimeException());
                return;
            }
            if (callId.startsWith("timeout_")) {
                responseObserver.onError(Status.DEADLINE_EXCEEDED
                        .withDescription("fake deadline").asRuntimeException());
                return;
            }

            ChatResponse.Builder resp = ChatResponse.newBuilder()
                    .setCallId(callId)
                    .setModel("fake-model")
                    .setProvider("fake")
                    .setCacheHit(false)
                    .setDurationMs(1)
                    .setCostCent(0L)
                    .setContent(deriveContent(request, callId));

            if (callId.startsWith("toolcall_")) {
                resp.addToolCalls(ToolCall.newBuilder()
                        .setCallId("tc_1")
                        .setToolId("t0")
                        .setInputJson("{\"q\":\"x\"}")
                        .setStatus("pending")
                        .build());
                resp.setContent("tool_call(t0, {\"q\":\"x\"})");
            } else if (callId.startsWith("tokens_")) {
                resp.setInputTokens(100);
                resp.setOutputTokens(50);
            } else {
                resp.setInputTokens(10);
                resp.setOutputTokens(5);
            }

            responseObserver.onNext(resp.build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(t);
        }
    }

    @Override
    public void streamChat(ChatRequest request, StreamObserver<ChatChunk> responseObserver) {
        lastRequest.set(request);
        String callId = request.getCallId();

        if (callId != null && callId.startsWith("unavailable_")) {
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("fake unavailable").asRuntimeException());
            return;
        }

        try {
            String full = deriveContent(request, callId);
            int mid = full.length() / 2;
            String d1 = mid > 0 ? full.substring(0, mid) : "";
            String d2 = mid > 0 ? full.substring(mid) : full;

            responseObserver.onNext(ChatChunk.newBuilder()
                    .setCallId(callId).setDelta(d1).build());
            responseObserver.onNext(ChatChunk.newBuilder()
                    .setCallId(callId).setDelta(d2).build());
            responseObserver.onNext(ChatChunk.newBuilder()
                    .setCallId(callId)
                    .setFinish(FinishReason.STOP).build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(t);
        }
    }

    /** Derive deterministic content from request + callId for response building. */
    private String deriveContent(ChatRequest request, String callId) {
        if (callId != null && callId.startsWith("final_")) {
            return "final_answer:done";
        }
        // Mirror last user message content (or echo "empty" if no user message)
        String lastUser = "";
        for (agentplatform.model.v1.Message msg : request.getMessagesList()) {
            if ("user".equals(msg.getRole())) {
                lastUser = msg.getContent();
            }
        }
        return lastUser.isEmpty() ? "empty" : lastUser;
    }
}
