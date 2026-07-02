package com.agent.modelgateway.grpc;

import agentplatform.common.v1.TraceContext;
import agentplatform.model.v1.ChatChunk;
import agentplatform.model.v1.ChatRequest;
import agentplatform.model.v1.FinishReason;
import agentplatform.model.v1.Message;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.modelgateway.api.AdapterRegistry;
import com.agent.modelgateway.api.CostMeter;
import com.agent.modelgateway.api.ModelProviderAdapter;
import com.agent.modelgateway.api.ModelRouter;
import com.agent.modelgateway.api.PromptCache;
import com.agent.modelgateway.api.QuotaEnforcer;
import com.agent.modelgateway.api.TokenCounter;
import com.agent.modelgateway.catalog.ModelCatalog;
import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.RouteResult;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ModelGatewayGrpcService} StreamChat 单测（Plan 07 T9，覆盖 UT-MG-009 + cancel）。
 *
 * <p>纯单测：mock {@link ModelRouter} / {@link AdapterRegistry} / {@link PromptCache} /
 * {@link QuotaEnforcer} / {@link CostMeter} / {@link ModelProviderAdapter}，
 * 使用真实 {@link ProtoMapper} + 真实 {@link GrpcExceptionAdvice}，
 * 用 capturing {@link StreamObserver} 捕获 onNext/onError/onCompleted。</p>
 *
 * <p>验证场景：</p>
 * <ul>
 *   <li>UT-MG-009：正常流式 → adapter.streamChat 返回 Flux&lt;ChatChunk&gt; → onNext 多个 chunk + onCompleted + CostMeter.record</li>
 *   <li>Cancel：客户端取消 → ServerCallStreamObserver.setOnCancelHandler → dispose 上游 Flux</li>
 *   <li>配额超限：QuotaEnforcer 抛 QUOTA_EXCEEDED → onError RESOURCE_EXHAUSTED</li>
 *   <li>适配器不存在 → onError UNKNOWN + MODEL_GATEWAY_ERROR</li>
 * </ul>
 */
@DisplayName("ModelGatewayGrpcService StreamChat server streaming（Plan 07 T9）")
class ModelGatewayGrpcServiceStreamTest {

    private ModelRouter modelRouter;
    private AdapterRegistry adapterRegistry;
    private PromptCache promptCache;
    private QuotaEnforcer quotaEnforcer;
    private CostMeter costMeter;
    private TokenCounter tokenCounter;
    private ModelCatalog modelCatalog;
    private ModelGatewayGrpcService grpcService;

    @BeforeEach
    void setUp() {
        modelRouter = mock(ModelRouter.class);
        adapterRegistry = mock(AdapterRegistry.class);
        promptCache = mock(PromptCache.class);
        quotaEnforcer = mock(QuotaEnforcer.class);
        costMeter = mock(CostMeter.class);
        tokenCounter = mock(TokenCounter.class);
        modelCatalog = mock(ModelCatalog.class);
        ProtoMapper mapper = new ProtoMapper();
        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();
        grpcService = new ModelGatewayGrpcService(
                modelRouter, adapterRegistry, promptCache, quotaEnforcer, costMeter,
                mapper, advice, tokenCounter, modelCatalog);
    }

    // ===== UT-MG-009: 正常流式 =====

    @Test
    @DisplayName("UT-MG-009: Should_StreamResponse_When_ServerStreamingRequested → 3 chunks + onCompleted")
    void should_StreamResponse_When_ServerStreamingRequested() {
        // given
        ChatRequest req = buildChatRequest("call-1", "intent", 1001L);
        when(modelRouter.route(any(Scene.class), anyString()))
                .thenReturn(new RouteResult("openai", "anthropic", true));
        ModelProviderAdapter adapter = mock(ModelProviderAdapter.class);
        when(adapterRegistry.get("openai")).thenReturn(adapter);
        // mock adapter.streamChat 返回 3 个 chunk（2 个 delta + 1 个 finish）
        Flux<ChatChunk> flux = Flux.just(
                ChatChunk.newBuilder().setDelta("Hello").build(),
                ChatChunk.newBuilder().setDelta(" world").build(),
                ChatChunk.newBuilder().setFinish(FinishReason.STOP).build());
        when(adapter.streamChat(any(), anyString())).thenReturn(flux);
        when(costMeter.record(any())).thenReturn(0.01);

        // when
        CapturingStreamObserver obs = new CapturingStreamObserver();
        grpcService.streamChat(req, obs);

        // then
        assertThat(obs.completed).isTrue();
        assertThat(obs.error).isNull();
        assertThat(obs.values).hasSize(3);
        // 每个 chunk 的 call_id 由 service 层补
        assertThat(obs.values.get(0).getCallId()).isEqualTo("call-1");
        assertThat(obs.values.get(0).getDelta()).isEqualTo("Hello");
        assertThat(obs.values.get(1).getDelta()).isEqualTo(" world");
        assertThat(obs.values.get(2).getFinish()).isEqualTo(FinishReason.STOP);
        // 验证调用链
        verify(quotaEnforcer).checkQuota("1001", 0.01);
        verify(adapter).streamChat(any(), anyString());
        verify(costMeter).record(any());
        // 验证不查 PromptCache（流式不缓存）
        verify(promptCache, never()).lookup(anyString(), anyString());
        verify(promptCache, never()).put(anyString(), anyString(), any());
    }

    // ===== Cancel 测试 =====

    @Test
    @DisplayName("Should_CancelStream_When_ClientCancelled → dispose 上游 Flux")
    void should_CancelStream_When_ClientCancelled() {
        // given
        ChatRequest req = buildChatRequest("call-2", "intent", 2002L);
        when(modelRouter.route(any(Scene.class), anyString()))
                .thenReturn(new RouteResult("openai", null, true));
        ModelProviderAdapter adapter = mock(ModelProviderAdapter.class);
        when(adapterRegistry.get("openai")).thenReturn(adapter);
        // 用 AtomicBoolean 跟踪上游 Flux 是否被 cancel
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Flux<ChatChunk> neverFlux = Flux.<ChatChunk>never()
                .doOnCancel(() -> cancelled.set(true));
        when(adapter.streamChat(any(), anyString())).thenReturn(neverFlux);

        // when
        CancelableStreamObserver obs = new CancelableStreamObserver();
        grpcService.streamChat(req, obs);

        // then: setOnCancelHandler 被注册
        assertThat(obs.cancelHandler).as("cancel handler 应被注册").isNotNull();
        // 触发客户端取消（模拟 gRPC 框架调用 onCancelHandler）
        obs.cancelHandler.run();
        // 验证上游 Flux 被 dispose → doOnCancel 触发
        assertThat(cancelled.get()).as("上游 Flux 应被 cancel").isTrue();
        // 验证未调用 costMeter.record（取消不计量）
        verify(costMeter, never()).record(any());
    }

    // ===== 配额超限 =====

    @Test
    @DisplayName("Should_ReturnQuotaExceeded_When_QuotaCheckFails → onError RESOURCE_EXHAUSTED")
    void should_ReturnQuotaExceeded_When_QuotaCheckFails() {
        // given
        ChatRequest req = buildChatRequest("call-3", "intent", 3003L);
        when(modelRouter.route(any(Scene.class), anyString()))
                .thenReturn(new RouteResult("openai", null, true));
        doThrow(new BusinessException(ErrorCode.QUOTA_EXCEEDED, "quota exceeded"))
                .when(quotaEnforcer).checkQuota(anyString(), anyDouble());

        // when
        CapturingStreamObserver obs = new CapturingStreamObserver();
        grpcService.streamChat(req, obs);

        // then
        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) obs.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(sre.getStatus().getDescription()).contains("QUOTA_EXCEEDED");
        // 验证未调用 adapter / costMeter
        verify(adapterRegistry, never()).get(anyString());
        verify(costMeter, never()).record(any());
    }

    // ===== 适配器不存在 =====

    @Test
    @DisplayName("Should_ReturnError_When_AdapterNotFound → onError UNKNOWN + MODEL_GATEWAY_ERROR")
    void should_ReturnError_When_AdapterNotFound() {
        // given
        ChatRequest req = buildChatRequest("call-4", "intent", 4004L);
        when(modelRouter.route(any(Scene.class), anyString()))
                .thenReturn(new RouteResult("unknown-provider", null, true));
        when(adapterRegistry.get("unknown-provider")).thenReturn(null);

        // when
        CapturingStreamObserver obs = new CapturingStreamObserver();
        grpcService.streamChat(req, obs);

        // then
        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) obs.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.UNKNOWN);
        assertThat(sre.getStatus().getDescription()).contains("MODEL_GATEWAY_ERROR");
        verify(costMeter, never()).record(any());
    }

    // ===== adapter.streamChat 抛异常 =====

    @Test
    @DisplayName("Should_ReturnError_When_AdapterStreamThrows → onError UNKNOWN + MODEL_GATEWAY_ERROR")
    void should_ReturnError_When_AdapterStreamThrows() {
        // given
        ChatRequest req = buildChatRequest("call-5", "intent", 5005L);
        when(modelRouter.route(any(Scene.class), anyString()))
                .thenReturn(new RouteResult("openai", null, true));
        ModelProviderAdapter adapter = mock(ModelProviderAdapter.class);
        when(adapterRegistry.get("openai")).thenReturn(adapter);
        // adapter.streamChat 抛 RuntimeException（模拟 ProviderUnavailable）
        when(adapter.streamChat(any(), anyString()))
                .thenThrow(new RuntimeException("provider stream unavailable"));

        // when
        CapturingStreamObserver obs = new CapturingStreamObserver();
        grpcService.streamChat(req, obs);

        // then
        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) obs.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.UNKNOWN);
        assertThat(sre.getStatus().getDescription()).contains("MODEL_GATEWAY_ERROR");
        verify(costMeter, never()).record(any());
    }

    // ===== helper =====

    private ChatRequest buildChatRequest(String callId, String scene, long tenantId) {
        return ChatRequest.newBuilder()
                .setCallId(callId)
                .setScene(scene)
                .setTrace(TraceContext.newBuilder()
                        .setTenantId(tenantId)
                        .setTraceId("trace-" + callId))
                .addMessages(Message.newBuilder()
                        .setRole("user")
                        .setContent("你好"))
                .build();
    }

    /** 捕获多个 chunk 的 StreamObserver（用于流式测试，普通 observer 无 cancel 支持）。 */
    private static class CapturingStreamObserver implements StreamObserver<ChatChunk> {
        final List<ChatChunk> values = new ArrayList<>();
        Throwable error;
        boolean completed;

        @Override
        public void onNext(ChatChunk value) { values.add(value); }
        @Override
        public void onError(Throwable t) { error = t; }
        @Override
        public void onCompleted() { completed = true; }
    }

    /**
     * 支持 cancel handler 的 StreamObserver（模拟 {@link ServerCallStreamObserver}）。
     *
     * <p>用于 cancel 测试：service 层检测 instanceof ServerCallStreamObserver 后
     * 调用 setOnCancelHandler，测试手动触发 handler 验证上游 Flux 被 dispose。</p>
     */
    private static class CancelableStreamObserver extends ServerCallStreamObserver<ChatChunk> {
        final List<ChatChunk> values = new ArrayList<>();
        Throwable error;
        boolean completed;
        Runnable cancelHandler;

        @Override
        public void onNext(ChatChunk value) { values.add(value); }
        @Override
        public void onError(Throwable t) { error = t; }
        @Override
        public void onCompleted() { completed = true; }

        @Override
        public void setOnCancelHandler(Runnable handler) { this.cancelHandler = handler; }

        @Override
        public boolean isReady() { return true; }

        @Override
        public void setOnReadyHandler(Runnable handler) { }

        @Override
        public void disableAutoInboundFlowControl() { }

        @Override
        public void request(int count) { }

        @Override
        public void setMessageCompression(boolean enable) { }

        @Override
        public void setCompression(String compression) { }

        @Override
        public boolean isCancelled() { return false; }
    }
}
