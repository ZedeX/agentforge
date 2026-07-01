package com.agent.modelgateway.grpc;

import agentplatform.common.v1.TraceContext;
import agentplatform.model.v1.ChatChunk;
import agentplatform.model.v1.ChatRequest;
import agentplatform.model.v1.ChatResponse;
import agentplatform.model.v1.Message;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.modelgateway.api.AdapterRegistry;
import com.agent.modelgateway.api.CostMeter;
import com.agent.modelgateway.api.ModelProviderAdapter;
import com.agent.modelgateway.api.ModelRouter;
import com.agent.modelgateway.api.PromptCache;
import com.agent.modelgateway.api.QuotaEnforcer;
import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.ChatReply;
import com.agent.modelgateway.model.RouteResult;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ModelGatewayGrpcService} 单测（Plan 07 T8，覆盖 UT-MG-008 / UT-MG-006）。
 *
 * <p>纯单测：mock {@link ModelRouter} / {@link AdapterRegistry} / {@link PromptCache} /
 * {@link QuotaEnforcer} / {@link CostMeter}，使用真实 {@link ProtoMapper} +
 * 真实 {@link GrpcExceptionAdvice}，用 capturing StreamObserver 捕获 onNext/onError。</p>
 *
 * <p>验证场景：</p>
 * <ul>
 *   <li>正常流：route + adapter.chat + costMeter.record + cache.put → onNext + onCompleted</li>
 *   <li>缓存命中：promptCache.lookup 返回非 null → 跳过 adapter / costMeter / cache.put</li>
 *   <li>UT-MG-006：QuotaEnforcer 抛 QUOTA_EXCEEDED → onError RESOURCE_EXHAUSTED</li>
 *   <li>UT-MG-008：Adapter 抛 RuntimeException → onError UNKNOWN + MODEL_GATEWAY_ERROR</li>
 *   <li>适配器不存在 → onError UNKNOWN + MODEL_GATEWAY_ERROR</li>
 *   <li>streamChat/countTokens/listModels 未实现 → onError UNKNOWN</li>
 * </ul>
 */
@DisplayName("ModelGatewayGrpcService Chat gRPC 服务（Plan 07 T8）")
class ModelGatewayGrpcServiceChatTest {

    private ModelRouter modelRouter;
    private AdapterRegistry adapterRegistry;
    private PromptCache promptCache;
    private QuotaEnforcer quotaEnforcer;
    private CostMeter costMeter;
    private ModelGatewayGrpcService grpcService;

    @BeforeEach
    void setUp() {
        modelRouter = mock(ModelRouter.class);
        adapterRegistry = mock(AdapterRegistry.class);
        promptCache = mock(PromptCache.class);
        quotaEnforcer = mock(QuotaEnforcer.class);
        costMeter = mock(CostMeter.class);
        ProtoMapper mapper = new ProtoMapper();
        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();
        grpcService = new ModelGatewayGrpcService(
                modelRouter, adapterRegistry, promptCache, quotaEnforcer, costMeter, mapper, advice);
    }

    // ===== 正常流 =====

    @Test
    @DisplayName("Should_ReturnChatResponse_When_RouteAndCallSuccess: 正常调用 → onNext + onCompleted")
    void should_ReturnChatResponse_When_RouteAndCallSuccess() {
        // given
        ChatRequest req = buildChatRequest("call-1", "intent", 1001L, true);
        when(modelRouter.route(any(Scene.class), anyString()))
                .thenReturn(new RouteResult("openai", "anthropic", true));
        ModelProviderAdapter adapter = mock(ModelProviderAdapter.class);
        when(adapterRegistry.get("openai")).thenReturn(adapter);
        when(promptCache.lookup(anyString(), anyString())).thenReturn(null);
        ChatReply reply = new ChatReply("openai", "gpt-4o", "hello world",
                10, 20, 150L, false);
        when(adapter.chat(any(), anyString())).thenReturn(reply);
        when(costMeter.record(any())).thenReturn(0.01);

        // when
        CapturingObserver<ChatResponse> obs = new CapturingObserver<>();
        grpcService.chat(req, obs);

        // then
        assertThat(obs.completed).isTrue();
        assertThat(obs.value).isNotNull();
        assertThat(obs.value.getCallId()).isEqualTo("call-1");
        assertThat(obs.value.getProvider()).isEqualTo("openai");
        assertThat(obs.value.getModel()).isEqualTo("gpt-4o");
        assertThat(obs.value.getContent()).isEqualTo("hello world");
        assertThat(obs.value.getInputTokens()).isEqualTo(10);
        assertThat(obs.value.getOutputTokens()).isEqualTo(20);
        assertThat(obs.value.getDurationMs()).isEqualTo(150);
        assertThat(obs.value.getCacheHit()).isFalse();

        // 验证调用链
        verify(modelRouter).route(Scene.INTENT, "gpt-4o");
        verify(promptCache).lookup("1001", "user: 你好");
        verify(quotaEnforcer).checkQuota("1001", 0.01);
        verify(adapter).chat(any(), anyString());
        verify(costMeter).record(any());
        verify(promptCache).put(anyString(), anyString(), any(ChatReply.class));
    }

    // ===== 缓存命中 =====

    @Test
    @DisplayName("Should_ReturnCachedResponse_When_PromptCacheHit: 缓存命中 → 跳过 adapter/costMeter/cache.put")
    void should_ReturnCachedResponse_When_PromptCacheHit() {
        // given
        ChatRequest req = buildChatRequest("call-2", "audit", 2002L, true);
        when(modelRouter.route(any(Scene.class), anyString()))
                .thenReturn(new RouteResult("anthropic", null, true));
        ChatReply cachedReply = new ChatReply("anthropic", "claude-3.5", "cached",
                5, 5, 10L, true);
        // prompt 由 buildChatRequest 拼接为 "user: 你好"
        when(promptCache.lookup("2002", "user: 你好")).thenReturn(cachedReply);

        // when
        CapturingObserver<ChatResponse> obs = new CapturingObserver<>();
        grpcService.chat(req, obs);

        // then
        assertThat(obs.completed).isTrue();
        assertThat(obs.value).isNotNull();
        assertThat(obs.value.getContent()).isEqualTo("cached");
        assertThat(obs.value.getCacheHit()).isTrue();
        assertThat(obs.value.getProvider()).isEqualTo("anthropic");

        // 验证跳过 adapter/costMeter/cache.put
        verify(adapterRegistry, never()).get(anyString());
        verify(quotaEnforcer, never()).checkQuota(anyString(), anyDouble());
        verify(costMeter, never()).record(any());
        verify(promptCache, never()).put(anyString(), anyString(), any());
    }

    // ===== UT-MG-006: 配额超限 =====

    @Test
    @DisplayName("UT-MG-006: Should_EnforceQuotaLimit_When_TenantExceedsQuota → onError RESOURCE_EXHAUSTED")
    void should_EnforceQuotaLimit_When_TenantExceedsQuota() {
        // given
        ChatRequest req = buildChatRequest("call-3", "intent", 3003L, false);
        when(modelRouter.route(any(Scene.class), anyString()))
                .thenReturn(new RouteResult("openai", null, true));
        when(promptCache.lookup(anyString(), anyString())).thenReturn(null);
        // QuotaEnforcer 抛 QUOTA_EXCEEDED
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.QUOTA_EXCEEDED, "quota exceeded"))
                .when(quotaEnforcer).checkQuota(anyString(), anyDouble());

        // when
        CapturingObserver<ChatResponse> obs = new CapturingObserver<>();
        grpcService.chat(req, obs);

        // then
        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) obs.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(sre.getStatus().getDescription()).contains("QUOTA_EXCEEDED");
        // 验证未调用 adapter / costMeter
        verify(adapterRegistry, never()).get(anyString());
        verify(costMeter, never()).record(any());
    }

    // ===== UT-MG-008: 适配器调用失败 =====

    @Test
    @DisplayName("UT-MG-008: Should_ReturnModelError_When_ProviderReturnsError → onError UNKNOWN")
    void should_ReturnModelError_When_ProviderReturnsError() {
        // given
        ChatRequest req = buildChatRequest("call-4", "intent", 4004L, false);
        when(modelRouter.route(any(Scene.class), anyString()))
                .thenReturn(new RouteResult("openai", null, true));
        when(promptCache.lookup(anyString(), anyString())).thenReturn(null);
        ModelProviderAdapter adapter = mock(ModelProviderAdapter.class);
        when(adapterRegistry.get("openai")).thenReturn(adapter);
        // Adapter 抛 RuntimeException（模拟 ProviderUnavailable）
        when(adapter.chat(any(), anyString()))
                .thenThrow(new RuntimeException("provider unavailable"));

        // when
        CapturingObserver<ChatResponse> obs = new CapturingObserver<>();
        grpcService.chat(req, obs);

        // then
        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) obs.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.UNKNOWN);
        assertThat(sre.getStatus().getDescription()).contains("MODEL_GATEWAY_ERROR");
        // 验证未调用 costMeter.record（失败不记录成本）
        verify(costMeter, never()).record(any());
    }

    // ===== 适配器不存在 =====

    @Test
    @DisplayName("Should_ReturnError_When_AdapterNotFound → onError UNKNOWN + MODEL_GATEWAY_ERROR")
    void should_ReturnError_When_AdapterNotFound() {
        // given
        ChatRequest req = buildChatRequest("call-5", "intent", 5005L, false);
        when(modelRouter.route(any(Scene.class), anyString()))
                .thenReturn(new RouteResult("unknown-provider", null, true));
        when(promptCache.lookup(anyString(), anyString())).thenReturn(null);
        when(adapterRegistry.get("unknown-provider")).thenReturn(null);

        // when
        CapturingObserver<ChatResponse> obs = new CapturingObserver<>();
        grpcService.chat(req, obs);

        // then
        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) obs.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.UNKNOWN);
        assertThat(sre.getStatus().getDescription()).contains("MODEL_GATEWAY_ERROR");
    }

    // ===== streamChat / countTokens / listModels 未实现 =====

    @Test
    @DisplayName("streamChat 未实现 → onError UNKNOWN")
    void should_ReturnError_When_StreamChatNotImplemented() {
        ChatRequest req = buildChatRequest("call-6", "intent", 6006L, false);
        CapturingObserver<ChatChunk> obs = new CapturingObserver<>();
        grpcService.streamChat(req, obs);

        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) obs.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.UNKNOWN);
        assertThat(sre.getStatus().getDescription()).contains("streamChat");
    }

    @Test
    @DisplayName("cache 不命中 + enableCache=false → 不查询缓存不写入缓存")
    void should_SkipCache_When_EnableCacheFalse() {
        // given
        ChatRequest req = buildChatRequest("call-7", "generic", 7007L, false);
        when(modelRouter.route(any(Scene.class), anyString()))
                .thenReturn(new RouteResult("openai", null, true));
        ModelProviderAdapter adapter = mock(ModelProviderAdapter.class);
        when(adapterRegistry.get("openai")).thenReturn(adapter);
        ChatReply reply = new ChatReply("openai", "gpt-4o", "ok",
                5, 5, 50L, false);
        when(adapter.chat(any(), anyString())).thenReturn(reply);
        when(costMeter.record(any())).thenReturn(0.005);

        // when
        CapturingObserver<ChatResponse> obs = new CapturingObserver<>();
        grpcService.chat(req, obs);

        // then
        assertThat(obs.completed).isTrue();
        // enableCache=false → 不查不写缓存
        verify(promptCache, never()).lookup(anyString(), anyString());
        verify(promptCache, never()).put(anyString(), anyString(), any());
    }

    // ===== helpers =====

    /**
     * 构造测试用 ChatRequest。
     *
     * @param callId      调用 ID
     * @param scene       场景（intent/audit/generic/planning/tool_call/summary）
     * @param tenantId    租户 ID
     * @param enableCache 是否启用缓存
     */
    private ChatRequest buildChatRequest(String callId, String scene, long tenantId, boolean enableCache) {
        return ChatRequest.newBuilder()
                .setCallId(callId)
                .setScene(scene)
                .setPreferredModel("gpt-4o")
                .addMessages(Message.newBuilder().setRole("user").setContent("你好").build())
                .setEnablePromptCache(enableCache)
                .setTrace(TraceContext.newBuilder()
                        .setTenantId(tenantId)
                        .setTraceId("trace-" + callId)
                        .build())
                .build();
    }

    private static class CapturingObserver<T> implements StreamObserver<T> {
        T value;
        Throwable error;
        boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
