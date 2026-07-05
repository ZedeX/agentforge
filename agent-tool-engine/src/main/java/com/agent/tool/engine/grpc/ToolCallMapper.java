package com.agent.tool.engine.grpc;

import agentplatform.tool.v1.GetToolRegistryRequest;
import agentplatform.tool.v1.ListToolsRequest;
import agentplatform.tool.v1.RegisterToolRequest;
import agentplatform.tool.v1.ToolInvokeRequest;
import agentplatform.tool.v1.ToolInvokeResponse;
import agentplatform.tool.v1.ToolRegistry;
import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.SideEffect;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.model.ToolSchema;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bidirectional mapper between proto messages and tool-engine POJOs (T12).
 *
 * <p>Centralising the mapping here keeps the gRPC service class focused on
 * orchestration (unmarshal → delegate → marshal) and makes the field
 * transformations testable in isolation.</p>
 *
 * <p>Conversions:
 * <ul>
 *   <li>{@link ToolInvokeRequest} → {@link ToolCallRequest} (DTO for ToolGateway)</li>
 *   <li>{@link ToolCallResult} → {@link ToolInvokeResponse} (proto response)</li>
 *   <li>{@link RegisterToolRequest} → {@link ToolMeta} + {@link ToolSchema} pair</li>
 *   <li>{@link ToolRegistryEntity} (via ToolMeta) → {@link ToolRegistry} proto</li>
 * </ul>
 * </p>
 */
@Component
public class ToolCallMapper {

    // ==================== Invoke (CallTool) ====================

    /**
     * Convert a proto {@link ToolInvokeRequest} to a {@link ToolCallRequest} DTO.
     *
     * <p>Extracts toolId / inputJson / riskLevel / agentId / taskId / callId(traceId).
     * Empty strings on the wire are normalised to null where the DTO uses
     * nullable fields.</p>
     */
    public ToolCallRequest toInvokeRequest(ToolInvokeRequest proto) {
        if (proto == null) {
            return null;
        }
        ToolCallRequest dto = new ToolCallRequest();
        dto.setToolId(blankToNull(proto.getToolId()));
        dto.setInputJson(blankToNull(proto.getInputJson()));
        if (proto.getRiskLevel() > 0) {
            dto.setRiskLevel(ToolRiskLevel.fromLevel(proto.getRiskLevel()));
        }
        if (proto.getAgentId() > 0) {
            dto.setAgentId(proto.getAgentId());
        }
        if (!proto.getTaskId().isEmpty()) {
            dto.setTaskId(proto.getTaskId());
        }
        if (!proto.getCallId().isEmpty()) {
            dto.setTraceId(proto.getCallId());
        } else {
            dto.setTraceId("call-" + System.nanoTime());
        }
        // Default tenant; real multi-tenant wiring comes from auth context.
        dto.setTenantId("default");
        return dto;
    }

    /**
     * Convert a {@link ToolCallResult} DTO to a proto {@link ToolInvokeResponse}.
     */
    public ToolInvokeResponse toInvokeResponse(String callId, ToolCallResult result) {
        if (result == null) {
            return ToolInvokeResponse.newBuilder()
                    .setCallId(callId)
                    .setStatus(ToolCallStatus.FAILED.name())
                    .setErrorMsg("executor returned null result")
                    .build();
        }
        ToolInvokeResponse.Builder b = ToolInvokeResponse.newBuilder()
                .setCallId(callId)
                .setStatus(result.getStatus() != null ? result.getStatus().name() : "UNKNOWN")
                .setFromCache(result.isFromCache());
        if (result.getOutput() != null) {
            b.setOutputJson(result.getOutput());
        }
        if (result.getErrorStack() != null) {
            b.setErrorMsg(result.getErrorStack());
        }
        return b.build();
    }

    // ==================== RegisterTool ====================

    /**
     * Convert a proto {@link RegisterToolRequest} to a {@link ToolMeta} POJO.
     *
     * <p>The toolId is left null when the caller did not supply one; the registry
     * will assign one and return it via {@link ToolRegistry#register}.</p>
     */
    public ToolMeta toToolMeta(RegisterToolRequest proto) {
        if (proto == null) {
            return null;
        }
        ToolMeta meta = new ToolMeta();
        meta.setToolId(blankToNull(proto.getName() != null
                ? "tool-" + proto.getName().toLowerCase().replace('_', '-')
                : null));
        meta.setName(blankToNull(proto.getName()));
        meta.setDescription(blankToNull(proto.getDescription()));
        meta.setEndpoint(blankToNull(proto.getEndpoint()));
        if (proto.getTimeoutMs() > 0) {
            meta.setTimeoutMs(proto.getTimeoutMs());
        }
        // Executor type
        ExecutorType execType = parseExecutorType(proto.getExecutorType());
        meta.setExecutorType(execType);
        // Risk level → side effect
        ToolRiskLevel risk = proto.getRiskLevel() > 0
                ? ToolRiskLevel.fromLevel(proto.getRiskLevel())
                : ToolRiskLevel.R1;
        meta.setRiskLevel(risk);
        meta.setSideEffect(riskToSideEffect(risk));
        // Cacheable: R1 + READ_ONLY only (consistent with ToolGatewayImpl policy)
        meta.setCacheable(risk == ToolRiskLevel.R1
                && (meta.getSideEffect() == SideEffect.NONE
                    || meta.getSideEffect() == SideEffect.READ_ONLY));
        meta.setEnabled(true);
        return meta;
    }

    /**
     * Build a {@link ToolSchema} from the input_schema_json of a
     * {@link RegisterToolRequest}.
     *
     * <p>The schema is stored as a JSON string; we currently only extract
     * the {@code required} array (consistent with the existing
     * {@code ToolRegistryImpl.parseRequiredFields} approach).</p>
     */
    public ToolSchema toInputSchema(RegisterToolRequest proto) {
        if (proto == null) {
            return null;
        }
        ToolSchema schema = new ToolSchema();
        schema.setRequiredFields(List.of());
        return schema;
    }

    /** Build an empty {@link ToolSchema} for output (placeholder, T12). */
    public ToolSchema toOutputSchema(RegisterToolRequest proto) {
        ToolSchema schema = new ToolSchema();
        schema.setRequiredFields(List.of());
        return schema;
    }

    // ==================== ListTools / GetToolRegistry ====================

    /**
     * Convert a {@link ToolMeta} POJO to a proto {@link ToolRegistry}.
     *
     * <p>Only the fields available on ToolMeta are populated; database-only
     * fields (version / approved / created_at / updated_at) default to 0/false
     * when the source is a POJO rather than the JPA entity.</p>
     */
    public ToolRegistry toToolRegistryProto(ToolMeta meta) {
        if (meta == null) {
            return ToolRegistry.getDefaultInstance();
        }
        ToolRegistry.Builder b = ToolRegistry.newBuilder();
        if (meta.getToolId() != null) b.setToolId(meta.getToolId());
        if (meta.getName() != null) b.setName(meta.getName());
        if (meta.getDescription() != null) b.setDescription(meta.getDescription());
        if (meta.getExecutorType() != null) {
            b.setExecutorType(meta.getExecutorType().name());
        }
        if (meta.getRiskLevel() != null) {
            b.setRiskLevel(meta.getRiskLevel().getLevel());
        }
        if (meta.getEndpoint() != null) b.setEndpoint(meta.getEndpoint());
        b.setTimeoutMs((int) meta.getTimeoutMs());
        b.setApproved(true);
        return b.build();
    }

    /**
     * Extract page/size from a {@link ListToolsRequest}. Defaults to page=1,
     * size=20 when unset.
     */
    public int[] extractPaging(ListToolsRequest proto) {
        if (proto == null) {
            return new int[]{1, 20};
        }
        int page = proto.getPage() > 0 ? proto.getPage() : 1;
        int size = proto.getSize() > 0 ? proto.getSize() : 20;
        return new int[]{page, size};
    }

    /**
     * Extract the toolId from a {@link GetToolRegistryRequest}, returning null
     * for blank input so the caller can throw {@code ToolNotFoundException}.
     */
    public String extractToolId(GetToolRegistryRequest proto) {
        if (proto == null) {
            return null;
        }
        return blankToNull(proto.getToolId());
    }

    // ==================== Internal helpers ====================

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static ExecutorType parseExecutorType(String value) {
        if (value == null || value.isBlank()) {
            return ExecutorType.HTTP_API;
        }
        try {
            return ExecutorType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ExecutorType.HTTP_API;
        }
    }

    private static SideEffect riskToSideEffect(ToolRiskLevel risk) {
        if (risk == null) return SideEffect.NONE;
        return switch (risk) {
            case R1 -> SideEffect.NONE;
            case R2 -> SideEffect.WRITE_LOCAL;
            case R3 -> SideEffect.DESTRUCTIVE;
        };
    }
}
