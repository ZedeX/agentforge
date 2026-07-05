package com.agent.runtime.api.impl;

import agentplatform.tool.v1.ToolInvokeRequest;
import agentplatform.tool.v1.ToolInvokeResponse;
import com.agent.runtime.api.dto.ToolInvokeResult;

/**
 * Proto ↔ DTO mapper for tool engine Invoke RPC (T5, doc 06-runtime §4.2).
 *
 * <p>Single responsibility: convert between {@code agentplatform.tool.v1.ToolInvokeRequest/Response}
 * generated classes and {@code com.agent.runtime.api.dto.*} plain POJOs. No business logic, no I/O.
 *
 * <p>Methods are stateless and package-private for unit testing.
 */
class ToolCallMapper {

    /** Convert DTO request → proto ToolInvokeRequest. */
    ToolInvokeRequest toProto(com.agent.runtime.api.dto.ToolInvokeRequest req) {
        return ToolInvokeRequest.newBuilder()
                .setCallId(nullSafe(req.getCallId()))
                .setTaskId(nullSafe(req.getTaskId()))
                .setStepNo(req.getStepNo())
                .setAgentId(req.getAgentId())
                .setToolId(nullSafe(req.getToolId()))
                .setToolVersion(req.getToolVersion())
                .setInputJson(nullSafe(req.getInputJson()))
                .setRiskLevel(req.getRiskLevel())
                .setPromptCacheKey(nullSafe(req.getPromptCacheKey()))
                .build();
    }

    /** Convert proto ToolInvokeResponse → DTO ToolInvokeResult. */
    ToolInvokeResult fromProto(ToolInvokeResponse resp) {
        return new ToolInvokeResult(
                resp.getCallId(),
                resp.getStatus(),
                resp.getOutputJson(),
                resp.getErrorCode(),
                resp.getErrorMsg(),
                resp.getDurationMs(),
                resp.getCostCent(),
                resp.getTokenUsed(),
                resp.getFromCache());
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
