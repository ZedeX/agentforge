package com.agent.tool.engine.recall;

import com.agent.tool.engine.entity.ToolRegistryEntity;
import com.agent.tool.engine.model.ToolRecallResult;
import com.agent.tool.engine.repository.ToolRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Keyword-based fallback recaller used when the memory gRPC service is
 * unavailable or times out.
 *
 * <p>Loads all enabled tools from {@link ToolRegistryRepository} and scores
 * each by keyword overlap between the query terms and the tool's
 * name/description. Returns the top-K results above a minimum score
 * threshold.</p>
 *
 * <p>Scoring:
 * <ul>
 *   <li>Tool name contains query term: +0.5 per term (max 1.0)</li>
 *   <li>Description contains query term: +0.3 per term (max 0.6)</li>
 *   <li>Tool ID exact match: +0.2</li>
 * </ul>
 * Threshold: 0.2.</p>
 */
@Component
public class KeywordFallbackRecaller {

    private static final Logger log = LoggerFactory.getLogger(KeywordFallbackRecaller.class);

    private static final double SCORE_THRESHOLD = 0.2;

    private final ToolRegistryRepository registryRepository;

    public KeywordFallbackRecaller(ToolRegistryRepository registryRepository) {
        this.registryRepository = registryRepository;
    }

    /**
     * Keyword-match tools against the query.
     *
     * @param tenantId tenant scope (reserved for future tenant filtering)
     * @param query    query text (tool name + description + params keywords)
     * @param topK     max results
     * @return scored results sorted by score desc, never null
     */
    public List<ToolRecallResult> recall(String tenantId, String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        String normalizedQuery = query.toLowerCase();
        String[] terms = normalizedQuery.split("\\s+");

        // Load enabled tools (status = 1 = ENABLED)
        List<ToolRegistryEntity> tools = registryRepository.findByStatus(1);
        List<ToolRecallResult> scored = new ArrayList<>();

        for (ToolRegistryEntity tool : tools) {
            double score = computeScore(tool, normalizedQuery, terms);
            if (score >= SCORE_THRESHOLD) {
                scored.add(new ToolRecallResult(
                        tool.getToolId(), tool.getName(), score));
            }
        }

        scored.sort(Comparator.comparingDouble(ToolRecallResult::getScore).reversed());
        int limit = Math.min(topK, scored.size());
        List<ToolRecallResult> result = new ArrayList<>(scored.subList(0, limit));
        log.debug("Keyword fallback recall: query='{}', candidates={}, returned={}",
                query, scored.size(), result.size());
        return result;
    }

    /**
     * Keyword-match with explicit toolId + params (builds query internally).
     */
    public List<ToolRecallResult> recall(String tenantId, String toolId,
                                          Map<String, Object> params, int topK) {
        // For fallback, we search by toolId first (exact match in registry)
        if (toolId != null && !toolId.isBlank()) {
            return registryRepository.findByToolId(toolId)
                    .map(tool -> List.of(new ToolRecallResult(
                            tool.getToolId(), tool.getName(), 1.0)))
                    .orElse(List.of());
        }
        // No toolId — build a query from params and do keyword search
        String query = RecallQueryBuilder.build(toolId, null, null, params);
        return recall(tenantId, query, topK);
    }

    private double computeScore(ToolRegistryEntity tool, String query, String[] terms) {
        String name = tool.getName() == null ? "" : tool.getName().toLowerCase();
        String desc = tool.getDescription() == null ? "" : tool.getDescription().toLowerCase();
        String toolId = tool.getToolId() == null ? "" : tool.getToolId().toLowerCase();

        double score = 0.0;
        // Name matches
        if (!name.isEmpty()) {
            for (String term : terms) {
                if (term.length() > 1 && name.contains(term)) {
                    score += 0.5;
                    break;
                }
            }
        }
        // Description matches
        if (!desc.isEmpty()) {
            for (String term : terms) {
                if (term.length() > 1 && desc.contains(term)) {
                    score += 0.3;
                    break;
                }
            }
        }
        // Tool ID exact match
        if (!toolId.isEmpty() && toolId.equals(query)) {
            score += 0.2;
        }
        return Math.min(score, 1.0);
    }
}
