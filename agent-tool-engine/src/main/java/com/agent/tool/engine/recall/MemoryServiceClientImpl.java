package com.agent.tool.engine.recall;

import agentplatform.memory.v1.RecallRequest;
import agentplatform.memory.v1.RecallResponse;
import agentplatform.memory.v1.RecalledMemory;
import agentplatform.memory.v1.MemoryServiceGrpc;
import com.agent.tool.engine.config.ToolEngineProperties;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.Deadline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * gRPC implementation of {@link MemoryServiceClient}.
 *
 * <p>Wraps {@link MemoryServiceGrpc.MemoryServiceBlockingStub} with a
 * configurable deadline (default 2s). On gRPC failures, throws
 * {@link MemoryServiceException} carrying the status code name so the
 * caller can decide whether to fall back.</p>
 *
 * <p>Activated only when {@code tool.memory-client.enabled=true}.</p>
 */
@Component
@ConditionalOnProperty(name = "tool.memory-client.enabled", havingValue = "true")
public class MemoryServiceClientImpl implements MemoryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MemoryServiceClientImpl.class);

    private final MemoryServiceGrpc.MemoryServiceBlockingStub stub;
    private final long timeoutMs;

    public MemoryServiceClientImpl(
            MemoryServiceGrpc.MemoryServiceBlockingStub stub,
            ToolEngineProperties properties) {
        this.stub = stub;
        this.timeoutMs = properties.getMemoryClient().getTimeoutMs();
    }

    @Override
    public List<RecalledMemoryDto> recallMemories(String tenantId, String query, int topK) {
        RecallRequest.Builder reqBuilder = RecallRequest.newBuilder()
                .setQuery(query == null ? "" : query)
                .setTopK(topK)
                .addStrategies("vector")
                .addStrategies("keyword");
        if (tenantId != null && !tenantId.isBlank()) {
            reqBuilder.setSessionId(tenantId);
        }

        RecallResponse response;
        try {
            response = stub.withDeadline(Deadline.after(timeoutMs, TimeUnit.MILLISECONDS))
                    .recall(reqBuilder.build());
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            throw new MemoryServiceException(code.name(),
                    "Recall RPC failed: " + e.getStatus(), e);
        } catch (Exception e) {
            throw new MemoryServiceException("UNKNOWN",
                    "Recall RPC unexpected error: " + e.getMessage(), e);
        }

        List<RecalledMemory> memories = response.getMemoriesList();
        List<RecalledMemoryDto> result = new ArrayList<>(memories.size());
        for (RecalledMemory m : memories) {
            result.add(new RecalledMemoryDto(
                    m.getMemoryId(),
                    m.getContent(),
                    m.getSourceType(),
                    m.getSourceTaskId(),
                    m.getImportanceScore(),
                    m.getRelevanceScore(),
                    m.getCreatedAt()
            ));
        }
        log.debug("recallMemories: tenantId={}, query='{}', topK={}, returned={}",
                tenantId, query, topK, result.size());
        return result;
    }

    @Override
    public boolean isAvailable() {
        return stub != null;
    }
}
