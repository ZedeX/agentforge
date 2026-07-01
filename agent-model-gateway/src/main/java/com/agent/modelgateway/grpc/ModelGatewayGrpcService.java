package com.agent.modelgateway.grpc;

import agentplatform.model.v1.ChatChunk;
import agentplatform.model.v1.ChatRequest;
import agentplatform.model.v1.ChatResponse;
import agentplatform.model.v1.CountTokensRequest;
import agentplatform.model.v1.CountTokensResponse;
import agentplatform.model.v1.ListModelsRequest;
import agentplatform.model.v1.ListModelsResponse;
import agentplatform.model.v1.Message;
import agentplatform.model.v1.ModelGatewayGrpc;
import agentplatform.model.v1.ModelInfo;
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
import com.agent.modelgateway.model.AdapterContext;
import com.agent.modelgateway.model.ChatReply;
import com.agent.modelgateway.model.ModelUsageLog;
import com.agent.modelgateway.model.RouteResult;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

/**
 * ModelGateway gRPC 服务端实现（Plan 07 T8，4 RPC）。
 *
 * <p>覆盖 {@link ModelGatewayGrpc.ModelGatewayImplBase} 的 4 个 RPC：
 * {@code chat}（已实现）/ {@code streamChat}（T9）/ {@code countTokens}（T10）/
 * {@code listModels}（T10）。T9/T10/T11 保留 default UNIMPLEMENTED。</p>
 *
 * <p>{@code chat()} 8 步流程（Plan 07 T8 Green）：</p>
 * <ol>
 *   <li>解析 request → scene / tenantId / messages（{@link ProtoMapper}）</li>
 *   <li>{@link ModelRouter#route} → {@link RouteResult}</li>
 *   <li>{@link PromptCache#lookup} 命中则直接返回（cacheHit=true）</li>
 *   <li>{@link QuotaEnforcer#checkQuota} 超限抛 QUOTA_EXCEEDED</li>
 *   <li>{@link AdapterRegistry#get}.chat(context, prompt) 调用上游模型</li>
 *   <li>{@link CostMeter#record} 落 model_usage_log</li>
 *   <li>{@link PromptCache#put} 写入缓存</li>
 *   <li>{@code onNext(response)} / {@code onCompleted()}</li>
 * </ol>
 *
 * <p>异常处理：适配器调用失败抛 RuntimeException 时，包装为
 * {@link BusinessException}({@link ErrorCode#MODEL_GATEWAY_ERROR})，
 * 由 {@link GrpcExceptionAdvice} 翻译为 gRPC Status UNKNOWN（对齐 UT-MG-008）。</p>
 */
@Slf4j
@GrpcService
public class ModelGatewayGrpcService extends ModelGatewayGrpc.ModelGatewayImplBase {

    /** 单次调用预估成本（USD）—— skeleton 阶段使用固定值，T12 深化替换为 TokenCounter 预估。 */
    private static final double ESTIMATED_COST_USD = 0.01;

    private final ModelRouter modelRouter;
    private final AdapterRegistry adapterRegistry;
    private final PromptCache promptCache;
    private final QuotaEnforcer quotaEnforcer;
    private final CostMeter costMeter;
    private final ProtoMapper mapper;
    private final GrpcExceptionAdvice exceptionAdvice;
    private final TokenCounter tokenCounter;
    private final ModelCatalog modelCatalog;

    public ModelGatewayGrpcService(ModelRouter modelRouter,
                                   AdapterRegistry adapterRegistry,
                                   PromptCache promptCache,
                                   QuotaEnforcer quotaEnforcer,
                                   CostMeter costMeter,
                                   ProtoMapper mapper,
                                   GrpcExceptionAdvice exceptionAdvice,
                                   TokenCounter tokenCounter,
                                   ModelCatalog modelCatalog) {
        this.modelRouter = modelRouter;
        this.adapterRegistry = adapterRegistry;
        this.promptCache = promptCache;
        this.quotaEnforcer = quotaEnforcer;
        this.costMeter = costMeter;
        this.mapper = mapper;
        this.exceptionAdvice = exceptionAdvice;
        this.tokenCounter = tokenCounter;
        this.modelCatalog = modelCatalog;
    }

    // ===== RPC 1: Chat（同步调用，Plan 07 T8） =====

    @Override
    public void chat(ChatRequest request, StreamObserver<ChatResponse> responseObserver) {
        try {
            ChatResponse response = doChat(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    /**
     * 执行 8 步 chat 流程（抽离便于单测直接调用，绕过 StreamObserver 捕获）。
     */
    ChatResponse doChat(ChatRequest request) {
        // Step 1: 解析 request → scene / tenantId / messages
        Scene scene = mapper.toScene(request.getScene());
        String tenantId = mapper.toTenantId(request.hasTrace() ? request.getTrace() : null);
        String traceId = mapper.toTraceId(request);
        String callId = request.getCallId();
        String prompt = mapper.toPrompt(request);
        boolean enableCache = request.getEnablePromptCache();
        log.debug("chat start callId={} tenantId={} scene={} traceId={}", callId, tenantId, scene, traceId);

        // Step 2: ModelRouter.route(scene, preferredModel) → RouteResult
        RouteResult route = modelRouter.route(scene, request.getPreferredModel());
        String primaryProvider = route.getPrimaryProviderCode();
        log.debug("chat route callId={} primary={} fallback={}", callId, primaryProvider, route.getFallbackProviderCode());

        // Step 3: PromptCache.lookup 命中则直接返回
        if (enableCache) {
            ChatReply cached = promptCache.lookup(tenantId, prompt);
            if (cached != null) {
                log.info("chat cache hit callId={} tenantId={}", callId, tenantId);
                return mapper.toChatResponse(cached, callId);
            }
        }

        // Step 4: QuotaEnforcer.checkQuota 超限抛 QUOTA_EXCEEDED
        quotaEnforcer.checkQuota(tenantId, ESTIMATED_COST_USD);

        // Step 5: AdapterRegistry.get(primary).chat(context, prompt)
        ModelProviderAdapter adapter = adapterRegistry.get(primaryProvider);
        if (adapter == null) {
            log.error("chat adapter not found callId={} provider={}", callId, primaryProvider);
            throw new BusinessException(ErrorCode.MODEL_GATEWAY_ERROR,
                    "适配器不存在: " + primaryProvider);
        }
        AdapterContext context = new AdapterContext(traceId, tenantId, scene,
                ProtoMapper.DEFAULT_TIMEOUT_MS, enableCache);
        ChatReply reply;
        try {
            reply = adapter.chat(context, prompt);
        } catch (RuntimeException ex) {
            // Plan 07 UT-MG-008: ProviderUnavailable → MODEL_GATEWAY_ERROR → gRPC UNKNOWN
            log.error("chat adapter call failed callId={} provider={} err={}",
                    callId, primaryProvider, ex.getMessage());
            throw new BusinessException(ErrorCode.MODEL_GATEWAY_ERROR,
                    "模型调用失败: " + ex.getMessage(), ex);
        }
        if (reply == null) {
            throw new BusinessException(ErrorCode.MODEL_GATEWAY_ERROR,
                    "模型返回空响应: " + primaryProvider);
        }

        // Step 6: CostMeter.record 落 model_usage_log
        ModelUsageLog usageLog = buildUsageLog(reply, tenantId, traceId, scene);
        costMeter.record(usageLog);

        // Step 7: PromptCache.put 写入缓存
        if (enableCache) {
            promptCache.put(tenantId, prompt, reply);
        }

        // Step 8: onNext(response) / onCompleted() —— 由调用方处理
        log.info("chat success callId={} provider={} model={} input={} output={} latencyMs={} costUsd={}",
                callId, reply.getProviderCode(), reply.getModelName(),
                reply.getInputTokens(), reply.getOutputTokens(), reply.getLatencyMs(), usageLog.getTotalCostUsd());
        return mapper.toChatResponse(reply, callId);
    }

    /**
     * 构造 ModelUsageLog（不持久化，仅传给 CostMeter 累计）。
     */
    private ModelUsageLog buildUsageLog(ChatReply reply, String tenantId, String traceId, Scene scene) {
        ModelUsageLog log = new ModelUsageLog();
        log.setTraceId(traceId);
        log.setTenantId(tenantId);
        log.setProviderCode(reply.getProviderCode());
        log.setModelName(reply.getModelName());
        log.setScene(scene);
        log.setInputTokens(reply.getInputTokens());
        log.setOutputTokens(reply.getOutputTokens());
        log.setLatencyMs(reply.getLatencyMs());
        log.setStatus("SUCCESS");
        log.setCreatedAt(System.currentTimeMillis());
        return log;
    }

    // ===== RPC 2: StreamChat（T9 占位，需 reactor-core/Flux） =====

    @Override
    public void streamChat(ChatRequest request, StreamObserver<ChatChunk> responseObserver) {
        // T9: server streaming，需 Flux + 背压 + cancel 处理，放 Wave 29 实现
        exceptionAdvice.translate(
                new BusinessException(ErrorCode.MODEL_GATEWAY_ERROR, "streamChat 未实现（T9）"),
                responseObserver);
    }

    // ===== RPC 3: CountTokens（Plan 07 T10） =====

    @Override
    public void countTokens(CountTokensRequest request, StreamObserver<CountTokensResponse> responseObserver) {
        try {
            int total = 0;
            List<Message> messages = request.getMessagesList();
            if (messages != null) {
                for (Message msg : messages) {
                    total += tokenCounter.count(msg.getContent());
                }
            }
            log.debug("countTokens model={} messages={} total={}",
                    request.getModel(), messages == null ? 0 : messages.size(), total);
            CountTokensResponse response = CountTokensResponse.newBuilder()
                    .setTokenCount(total)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    // ===== RPC 4: ListModels（Plan 07 T10） =====

    @Override
    public void listModels(ListModelsRequest request, StreamObserver<ListModelsResponse> responseObserver) {
        try {
            String tier = request.getTier();
            List<ModelInfo> models = modelCatalog.list(tier);
            log.debug("listModels tier={} count={}", tier, models.size());
            ListModelsResponse response = ListModelsResponse.newBuilder()
                    .addAllModels(models)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }
}
