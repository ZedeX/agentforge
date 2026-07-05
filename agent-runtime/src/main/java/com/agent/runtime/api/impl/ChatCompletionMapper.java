package com.agent.runtime.api.impl;

import agentplatform.model.v1.ChatChunk;
import agentplatform.model.v1.ChatRequest;
import agentplatform.model.v1.ChatResponse;
import agentplatform.model.v1.FinishReason;
import agentplatform.model.v1.Message;
import agentplatform.model.v1.ModelParams;
import agentplatform.model.v1.ToolCall;
import com.agent.runtime.api.dto.ModelChatChunk;
import com.agent.runtime.api.dto.ModelChatRequest;
import com.agent.runtime.api.dto.ModelChatResponse;
import com.agent.runtime.api.dto.ModelMessage;
import com.agent.runtime.api.dto.ModelToolCall;
import com.agent.runtime.api.dto.TokenUsage;

import java.util.ArrayList;
import java.util.List;

/**
 * Proto ↔ DTO mapper for model gateway Chat/StreamChat RPC (T4, doc 06-runtime §3.2).
 *
 * <p>Single responsibility: convert between {@code agentplatform.model.v1.*} generated classes
 * and {@code com.agent.runtime.api.dto.*} plain POJOs. No business logic, no I/O.
 *
 * <p>Methods are stateless and package-private for unit testing via {@code ChatCompletionMapperTest}.
 */
class ChatCompletionMapper {

    /** Convert DTO request → proto ChatRequest. */
    ChatRequest toProto(ModelChatRequest req) {
        ChatRequest.Builder b = ChatRequest.newBuilder()
                .setCallId(nullSafe(req.getCallId()))
                .setTaskId(nullSafe(req.getTaskId()))
                .setScene(nullSafe(req.getScene()))
                .setTier(nullSafe(req.getTier()))
                .setPreferredModel(nullSafe(req.getPreferredModel()))
                .setEnablePromptCache(req.isEnablePromptCache());

        for (ModelMessage msg : req.getMessages()) {
            b.addMessages(toProtoMessage(msg));
        }

        ModelParams.Builder params = ModelParams.newBuilder()
                .setTemperature(req.getTemperature())
                .setMaxTokens(req.getMaxTokens())
                .setTopP(req.getTopP())
                .setEnableCot(req.isEnableCot())
                .setRequireSource(req.isRequireSource());
        for (String stop : req.getStop()) {
            params.addStop(stop);
        }
        b.setParams(params);

        return b.build();
    }

    Message toProtoMessage(ModelMessage msg) {
        Message.Builder b = Message.newBuilder()
                .setRole(nullSafe(msg.getRole()))
                .setContent(nullSafe(msg.getContent()));
        if (msg.getToolCall() != null) {
            b.addToolCalls(toProtoToolCall(msg.getToolCall()));
        }
        if (msg.getToolCallId() != null) {
            b.setToolCallId(msg.getToolCallId());
        }
        return b.build();
    }

    ToolCall toProtoToolCall(ModelToolCall tc) {
        return ToolCall.newBuilder()
                .setCallId(nullSafe(tc.getCallId()))
                .setToolId(nullSafe(tc.getToolId()))
                .setInputJson(nullSafe(tc.getInputJson()))
                .setStatus(nullSafe(tc.getStatus()))
                .build();
    }

    /** Convert proto ChatResponse → DTO ModelChatResponse. */
    ModelChatResponse fromProto(ChatResponse resp) {
        List<ModelToolCall> toolCalls = new ArrayList<>();
        for (ToolCall tc : resp.getToolCallsList()) {
            toolCalls.add(new ModelToolCall(
                    tc.getCallId(), tc.getToolId(), tc.getInputJson(), tc.getStatus()));
        }
        TokenUsage usage = new TokenUsage(
                resp.getInputTokens(), resp.getOutputTokens());
        return new ModelChatResponse(
                resp.getCallId(),
                resp.getModel(),
                resp.getProvider(),
                resp.getContent(),
                toolCalls,
                usage,
                resp.getCostCent(),
                resp.getCacheHit(),
                resp.getDurationMs());
    }

    /** Convert proto ChatChunk → DTO ModelChatChunk. */
    ModelChatChunk fromProto(ChatChunk chunk) {
        ModelToolCall toolCall = null;
        ModelChatChunk.FinishReason finish = ModelChatChunk.FinishReason.NONE;
        switch (chunk.getExtraCase()) {
            case TOOL_CALL:
                ToolCall tc = chunk.getToolCall();
                toolCall = new ModelToolCall(
                        tc.getCallId(), tc.getToolId(), tc.getInputJson(), tc.getStatus());
                finish = ModelChatChunk.FinishReason.TOOL_CALLS;
                break;
            case FINISH:
                finish = mapFinish(chunk.getFinish());
                break;
            case EXTRA_NOT_SET:
            default:
                // keep defaults
        }
        return new ModelChatChunk(chunk.getCallId(), chunk.getDelta(), toolCall, finish);
    }

    ModelChatChunk.FinishReason mapFinish(FinishReason proto) {
        if (proto == null) return ModelChatChunk.FinishReason.NONE;
        switch (proto) {
            case STOP: return ModelChatChunk.FinishReason.STOP;
            case LENGTH: return ModelChatChunk.FinishReason.LENGTH;
            case TOOL_CALLS: return ModelChatChunk.FinishReason.TOOL_CALLS;
            case CONTENT_FILTER: return ModelChatChunk.FinishReason.CONTENT_FILTER;
            case FINISH_REASON_UNSPECIFIED:
            default:
                return ModelChatChunk.FinishReason.NONE;
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
