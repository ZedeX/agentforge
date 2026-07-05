package com.agent.tool.engine.recall;

import com.agent.tool.engine.api.ToolSemanticRecaller;
import com.agent.tool.engine.model.ToolRecallResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * T10 tool semantic recaller: calls agent-memory Recall RPC for historical
 * tool-call experience, falls back to keyword matching on failure.
 *
 * <p>Orchestration flow:
 * <ol>
 *   <li>Build query text via {@link RecallQueryBuilder} (toolId + params).</li>
 *   <li>Call {@link MemoryServiceClient#recallMemories} with topK.</li>
 *   <li>On success: filter memories with importance &lt; 0.4, sort by
 *       relevance score desc, take topK, map to {@link ToolRecallResult}.</li>
 *   <li>On failure (UNAVAILABLE / DEADLINE_EXCEEDED): log warning and
 *       delegate to {@link KeywordFallbackRecaller}.</li>
 *   <li>On no hits: return empty list (never throws).</li>
 * </ol>
 * </p>
 *
 * <p>When {@link MemoryServiceClient} bean is absent (memory-client disabled),
 * the recaller goes directly to keyword fallback.</p>
 */
@Component
public class ToolSemanticRecallerImpl implements ToolSemanticRecaller {

    private static final Logger log = LoggerFactory.getLogger(ToolSemanticRecallerImpl.class);

    /** Memories below this importance are filtered out. */
    static final double IMPORTANCE_THRESHOLD = 0.4;

    private final MemoryServiceClient memoryClient;
    private final KeywordFallbackRecaller keywordFallback;

    /**
     * Spring constructor: injects memory service client (optional) and keyword
     * fallback (required). The same constructor is used by tests with explicit
     * mocks — {@code required = false} allows {@code memoryClient} to be null
     * when {@code tool.memory-client.enabled=false}.
     *
     * @param memoryClient    memory service client (may be null when
     *                        tool.memory-client.enabled=false)
     * @param keywordFallback keyword fallback recaller (always present)
     */
    @Autowired
    public ToolSemanticRecallerImpl(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            MemoryServiceClient memoryClient,
            KeywordFallbackRecaller keywordFallback) {
        this.memoryClient = memoryClient;
        this.keywordFallback = keywordFallback;
    }

    @Override
    public List<ToolRecallResult> recall(String tenantId, String toolId,
                                          Map<String, Object> params, int topK) {
        if (topK <= 0) {
            return List.of();
        }
        String query = RecallQueryBuilder.build(toolId, null, null, params);
        if (query.isBlank()) {
            log.debug("recall: empty query (toolId={}, params={}), returning empty", toolId, params);
            return List.of();
        }

        // Step 1: try memory service
        if (memoryClient != null && memoryClient.isAvailable()) {
            try {
                List<RecalledMemoryDto> memories = memoryClient.recallMemories(tenantId, query, topK);
                List<ToolRecallResult> results = mapAndFilter(memories, topK);
                if (!results.isEmpty()) {
                    log.debug("recall: memory hit, toolId={}, results={}", toolId, results.size());
                    return results;
                }
                log.debug("recall: memory returned no hits for toolId={}, falling back to keyword", toolId);
            } catch (MemoryServiceException e) {
                if (e.isRecoverable()) {
                    log.warn("recall: memory service {} for toolId={}, falling back to keyword: {}",
                            e.getGrpcStatus(), toolId, e.getMessage());
                } else {
                    log.error("recall: memory service non-recoverable error for toolId={}: {}",
                            toolId, e.getMessage(), e);
                }
                // Fall through to keyword fallback
            } catch (Exception e) {
                log.warn("recall: memory service unexpected error for toolId={}, falling back: {}",
                        toolId, e.getMessage());
                // Fall through to keyword fallback
            }
        }

        // Step 2: keyword fallback
        return keywordFallback.recall(tenantId, toolId, params, topK);
    }

    @Override
    public List<ToolRecallResult> recall(String query, int topK) {
        // Legacy path: treat query as raw text, try memory then keyword
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        if (memoryClient != null && memoryClient.isAvailable()) {
            try {
                List<RecalledMemoryDto> memories = memoryClient.recallMemories(null, query, topK);
                List<ToolRecallResult> results = mapAndFilter(memories, topK);
                if (!results.isEmpty()) {
                    return results;
                }
            } catch (MemoryServiceException e) {
                log.warn("recall(legacy): memory service error, falling back: {}", e.getMessage());
            } catch (Exception e) {
                log.warn("recall(legacy): unexpected error, falling back: {}", e.getMessage());
            }
        }
        return keywordFallback.recall(null, query, topK);
    }

    // ==================== Internal mapping ====================

    /**
     * Map recalled memories to {@link ToolRecallResult}, filter by importance,
     * sort by relevance score desc, take topK.
     */
    private List<ToolRecallResult> mapAndFilter(List<RecalledMemoryDto> memories, int topK) {
        if (memories == null || memories.isEmpty()) {
            return List.of();
        }
        List<ToolRecallResult> results = new ArrayList<>(memories.size());
        for (RecalledMemoryDto memory : memories) {
            if (memory.getImportanceScore() < IMPORTANCE_THRESHOLD) {
                continue;
            }
            ToolRecallResult result = new ToolRecallResult();
            result.setToolId(memory.getMemoryId());
            result.setName(memory.getSourceType());
            result.setScore(memory.getRelevanceScore());
            result.setContent(memory.getContent());
            result.setSourceTaskId(memory.getSourceTaskId());
            result.setImportance(memory.getImportanceScore());
            results.add(result);
        }
        results.sort(Comparator.comparingDouble(ToolRecallResult::getScore).reversed());
        int limit = Math.min(topK, results.size());
        return new ArrayList<>(results.subList(0, limit));
    }
}
