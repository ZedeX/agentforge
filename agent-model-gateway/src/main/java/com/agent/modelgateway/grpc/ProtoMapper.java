package com.agent.modelgateway.grpc;

import agentplatform.common.v1.TraceContext;
import agentplatform.model.v1.ChatRequest;
import agentplatform.model.v1.ChatResponse;
import agentplatform.model.v1.Message;
import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.AdapterContext;
import com.agent.modelgateway.model.ChatReply;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Proto ↔ 内部模型映射器（Plan 07 T8）。
 *
 * <p>职责：将 gRPC {@link ChatRequest} 解析为业务层所需的
 * {@link AdapterContext} + prompt 字符串，并将 {@link ChatReply} 映射回
 * {@link ChatResponse}。所有 proto/内部模型转换集中在此，避免业务层耦合 proto 生成类。</p>
 *
 * <p>关键映射规则：</p>
 * <ul>
 *   <li>scene: proto 5 种（intent/planning/tool_call/summary/audit）→ 内部 3 种
 *       （INTENT/AUDIT/GENERIC），未识别的归 GENERIC</li>
 *   <li>tenantId: proto {@code int64 tenant_id} → String（业务层统一用 String）</li>
 *   <li>traceId: 优先取 {@link TraceContext#getTraceId}，回退 {@code call_id}</li>
 *   <li>prompt: 由 {@code messages} 拼接（每条 {@code role: content}），便于 mock adapter 处理</li>
 *   <li>timeoutMs: proto 未携带，使用默认 60000ms</li>
 * </ul>
 */
@Component
public class ProtoMapper {

    /** 默认调用超时 60 秒（proto ChatRequest 未携带 timeout，使用此默认值）。 */
    public static final long DEFAULT_TIMEOUT_MS = 60_000L;

    /**
     * 解析 ChatRequest 为 AdapterContext。
     *
     * @param request gRPC 请求
     * @return 适配器调用上下文
     */
    public AdapterContext toAdapterContext(ChatRequest request) {
        Scene scene = toScene(request.getScene());
        String tenantId = toTenantId(request.getTrace());
        String traceId = toTraceId(request);
        boolean enableCache = request.getEnablePromptCache();
        return new AdapterContext(traceId, tenantId, scene, DEFAULT_TIMEOUT_MS, enableCache);
    }

    /**
     * 从 ChatRequest.messages 拼接 prompt 字符串。
     *
     * <p>拼接格式：每条消息 {@code role: content}，换行分隔。
     * 业务层 ModelProviderAdapter.chat(context, prompt) 接收此字符串。</p>
     *
     * @param request gRPC 请求
     * @return 拼接后的 prompt 字符串
     */
    public String toPrompt(ChatRequest request) {
        List<Message> messages = request.getMessagesList();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(msg.getRole()).append(": ").append(msg.getContent());
        }
        return sb.toString();
    }

    /**
     * 将 ChatReply 映射为 proto ChatResponse，并回填 callId。
     *
     * @param reply  适配器返回
     * @param callId 原始请求 call_id
     * @return proto ChatResponse
     */
    public ChatResponse toChatResponse(ChatReply reply, String callId) {
        return ChatResponse.newBuilder()
                .setCallId(callId)
                .setModel(reply.getModelName())
                .setProvider(reply.getProviderCode())
                .setContent(reply.getContent())
                .setInputTokens(reply.getInputTokens())
                .setOutputTokens(reply.getOutputTokens())
                .setCacheHit(reply.isCacheHit())
                .setDurationMs((int) reply.getLatencyMs())
                .build();
    }

    /**
     * proto scene 字符串 → 内部 {@link Scene} 枚举。
     * proto 5 种（intent/planning/tool_call/summary/audit）→ 内部 3 种。
     */
    public Scene toScene(String sceneCode) {
        if (sceneCode == null || sceneCode.isEmpty()) {
            return Scene.GENERIC;
        }
        String lower = sceneCode.toLowerCase();
        if ("intent".equals(lower)) {
            return Scene.INTENT;
        }
        if ("audit".equals(lower)) {
            return Scene.AUDIT;
        }
        // planning / tool_call / summary / 其他 → GENERIC
        return Scene.GENERIC;
    }

    /**
     * 从 TraceContext 解析租户 ID（int64 → String）。
     */
    public String toTenantId(TraceContext trace) {
        if (trace == null) {
            return "default";
        }
        long tenantId = trace.getTenantId();
        if (tenantId <= 0) {
            return "default";
        }
        return String.valueOf(tenantId);
    }

    /**
     * 解析 traceId：优先 TraceContext.trace_id，回退 call_id。
     */
    public String toTraceId(ChatRequest request) {
        if (request.hasTrace() && !request.getTrace().getTraceId().isEmpty()) {
            return request.getTrace().getTraceId();
        }
        return request.getCallId();
    }
}
