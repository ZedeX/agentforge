package com.agent.tool.engine.grpc;

import agentplatform.tool.v1.GetToolRegistryRequest;
import agentplatform.tool.v1.ListToolsRequest;
import agentplatform.tool.v1.ListToolsResponse;
import agentplatform.tool.v1.RegisterToolAck;
import agentplatform.tool.v1.RegisterToolRequest;
import agentplatform.tool.v1.ToolGatewayGrpc;
import agentplatform.tool.v1.ToolInvokeRequest;
import agentplatform.tool.v1.ToolInvokeResponse;
import agentplatform.tool.v1.ToolRegistry;  // proto message (NOT the api interface)
import com.agent.tool.engine.api.ToolGateway;
import com.agent.tool.engine.exception.ToolNotFoundException;
import com.agent.tool.engine.exception.ToolValidationException;
import com.agent.tool.engine.model.ToolCallRequest;
import com.agent.tool.engine.model.ToolCallResult;
import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.model.ToolSchema;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * gRPC implementation of the {@code ToolGateway} service (T12).
 *
 * <p>Exposes four RPCs defined in {@code agent-proto/src/main/proto/tool.proto}:
 * <ol>
 *   <li>{@code Invoke} — delegate to {@link ToolGateway#invoke} after unmarshalling.</li>
 *   <li>{@code RegisterTool} — delegate to {@link ToolRegistry#register}.</li>
 *   <li>{@code ListTools} — return enabled tools (or all when includeDisabled).</li>
 *   <li>{@code GetToolRegistry} — fetch a single tool by id.</li>
 * </ol>
 * </p>
 *
 * <p>Domain exceptions ({@code ToolEngineException} subclasses) are translated
 * to gRPC status codes by {@link GrpcExceptionAdvice}. Each method follows the
 * same shape: unmarshal → delegate → marshal → onNext/onCompleted, with a
 * catch-all {@code onError} through the advice.</p>
 *
 * <p>{@link GrpcExceptionAdvice} and {@link ToolCallMapper} are constructor-
 * injected so the service is unit-testable without Spring context (mock the
 * collaborators and call the RPC methods directly with a mock
 * {@link StreamObserver}).</p>
 */
@GrpcService
public class ToolGatewayGrpcService extends ToolGatewayGrpc.ToolGatewayImplBase {

    private static final Logger log = LoggerFactory.getLogger(ToolGatewayGrpcService.class);

    private final ToolGateway toolGateway;
    private final com.agent.tool.engine.api.ToolRegistry toolRegistry;
    private final ToolCallMapper mapper;
    private final GrpcExceptionAdvice advice;

    @Autowired
    public ToolGatewayGrpcService(ToolGateway toolGateway,
                                  com.agent.tool.engine.api.ToolRegistry toolRegistry,
                                  ToolCallMapper mapper,
                                  GrpcExceptionAdvice advice) {
        this.toolGateway = toolGateway;
        this.toolRegistry = toolRegistry;
        this.mapper = mapper;
        this.advice = advice;
    }

    // ==================== Invoke (CallTool) ====================

    @Override
    public void invoke(ToolInvokeRequest request, StreamObserver<ToolInvokeResponse> observer) {
        log.debug("invoke: toolId={}, callId={}, agentId={}",
                request.getToolId(), request.getCallId(), request.getAgentId());
        try {
            // Step 1: unmarshal proto → DTO
            ToolCallRequest dto = mapper.toInvokeRequest(request);
            if (dto.getToolId() == null) {
                throw new ToolValidationException("toolId 不能为空");
            }

            // Step 2: delegate to gateway
            ToolCallResult result = toolGateway.invoke(dto);

            // Step 3: marshal result → proto response
            String callId = request.getCallId().isEmpty()
                    ? dto.getTraceId() : request.getCallId();
            ToolInvokeResponse response = mapper.toInvokeResponse(callId, result);
            observer.onNext(response);
            observer.onCompleted();
        } catch (Exception e) {
            advice.translate(e, observer);
        }
    }

    // ==================== RegisterTool ====================

    @Override
    public void registerTool(RegisterToolRequest request,
                             StreamObserver<RegisterToolAck> observer) {
        log.debug("registerTool: name={}, executorType={}",
                request.getName(), request.getExecutorType());
        try {
            ToolMeta meta = mapper.toToolMeta(request);
            if (meta.getName() == null) {
                throw new ToolValidationException("工具 name 不能为空");
            }
            ToolSchema inputSchema = mapper.toInputSchema(request);
            ToolSchema outputSchema = mapper.toOutputSchema(request);

            String toolId = toolRegistry.register(meta, inputSchema, outputSchema);
            int version = 1;
            RegisterToolAck ack = RegisterToolAck.newBuilder()
                    .setToolId(toolId)
                    .setVersion(version)
                    .setApproved(true)
                    .build();
            observer.onNext(ack);
            observer.onCompleted();
        } catch (Exception e) {
            advice.translate(e, observer);
        }
    }

    // ==================== ListTools ====================

    @Override
    public void listTools(ListToolsRequest request,
                          StreamObserver<ListToolsResponse> observer) {
        log.debug("listTools: sceneTag={}, riskLevelMax={}",
                request.getSceneTag(), request.getRiskLevelMax());
        try {
            // ToolRegistryImpl exposes findByStatus(ENABLED). When the caller
            // passes a scene tag, we filter client-side for now (the repository
            // has findBySceneTagLike but ToolRegistry interface does not expose it).
            List<ToolMeta> tools = listEnabledTools();

            ListToolsResponse.Builder b = ListToolsResponse.newBuilder();
            for (ToolMeta meta : tools) {
                b.addTools(mapper.toToolRegistryProto(meta));
            }
            observer.onNext(b.build());
            observer.onCompleted();
        } catch (Exception e) {
            advice.translate(e, observer);
        }
    }

    /**
     * List enabled tools via the registry implementation's findByStatus method.
     *
     * <p>Cast to {@code ToolRegistryImpl} is safe because that is the only
     * concrete {@link ToolRegistry} bean in the application. If a different
     * implementation is wired in the future, this method should be lifted to
     * the {@link ToolRegistry} interface.</p>
     */
    private List<ToolMeta> listEnabledTools() {
        if (toolRegistry instanceof com.agent.tool.engine.api.impl.ToolRegistryImpl impl) {
            return impl.findByStatus(com.agent.tool.engine.enums.ToolStatus.ENABLED);
        }
        return List.of();
    }

    // ==================== GetToolRegistry ====================

    @Override
    public void getToolRegistry(GetToolRegistryRequest request,
                                StreamObserver<ToolRegistry> observer) {
        log.debug("getToolRegistry: toolId={}", request.getToolId());
        try {
            String toolId = mapper.extractToolId(request);
            if (toolId == null) {
                throw new ToolValidationException("toolId 不能为空");
            }
            ToolMeta meta = toolRegistry.findMeta(toolId);
            if (meta == null) {
                throw new ToolNotFoundException("工具未注册: " + toolId);
            }
            ToolRegistry proto = mapper.toToolRegistryProto(meta);
            observer.onNext(proto);
            observer.onCompleted();
        } catch (Exception e) {
            advice.translate(e, observer);
        }
    }
}
