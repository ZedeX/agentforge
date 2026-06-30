package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ToolSemanticRecaller;
import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.model.ToolRecallResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * F8.D1/D2 工具语义召回实现 (recall + rerank)。
 *
 * <p>骨架阶段使用关键词匹配模拟语义召回:
 * <ul>
 *   <li>名称完全包含: +0.6</li>
 *   <li>名称被 query 包含: +0.4</li>
 *   <li>描述关键词命中: +0.2</li>
 * </ul>
 * 分数 < 0.3 阈值的不返回, 命中结果按 score 降序取 Top-K。
 * 生产实现应替换为向量检索 (FAISS / Milvus)。</p>
 */
@Component
public class ToolSemanticRecallerImpl implements ToolSemanticRecaller {

    private static final Logger log = LoggerFactory.getLogger(ToolSemanticRecallerImpl.class);

    /** 召回分数阈值, 低于此分数不返回。 */
    private static final double SCORE_THRESHOLD = 0.3;

    /** 工具索引: toolId -> ToolMeta。 */
    private final Map<String, ToolMeta> index = new ConcurrentHashMap<>();

    @Override
    public List<ToolRecallResult> recall(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            log.debug("召回参数非法 (query='{}', topK={}), 返回空", query, topK);
            return List.of();
        }
        String normalizedQuery = query.toLowerCase();
        List<ToolRecallResult> scored = new ArrayList<>();

        for (ToolMeta meta : index.values()) {
            double score = computeScore(meta, normalizedQuery);
            if (score >= SCORE_THRESHOLD) {
                scored.add(new ToolRecallResult(meta.getToolId(), meta.getName(), score));
            }
        }
        // 按 score 降序排序 (rerank)
        scored.sort(Comparator.comparingDouble(ToolRecallResult::getScore).reversed());

        int limit = Math.min(topK, scored.size());
        List<ToolRecallResult> result = new ArrayList<>(scored.subList(0, limit));
        log.debug("召回 query='{}', 命中={}, 返回={}", query, scored.size(), result.size());
        return result;
    }

    private double computeScore(ToolMeta meta, String query) {
        String name = meta.getName() == null ? "" : meta.getName().toLowerCase();
        String desc = meta.getDescription() == null ? "" : meta.getDescription().toLowerCase();

        double score = 0.0;
        // 名称完全包含 (query 中包含完整 name)
        if (!name.isEmpty() && query.contains(name)) {
            score += 0.6;
        }
        // 名称被 query 包含 (name 中包含 query 片段)
        if (!name.isEmpty() && name.contains(query)) {
            score += 0.4;
        }
        // 描述关键词命中
        if (!desc.isEmpty()) {
            String[] queryTokens = query.split("\\s+");
            for (String token : queryTokens) {
                if (token.length() > 1 && desc.contains(token)) {
                    score += 0.2;
                    break;
                }
            }
        }
        return Math.min(score, 1.0);
    }

    /**
     * 索引工具元数据 (供测试 / 上层注册使用)。
     *
     * @param meta 工具元数据, 必须携带 toolId
     */
    public void index(ToolMeta meta) {
        if (meta == null || meta.getToolId() == null || meta.getToolId().isBlank()) {
            log.warn("索引工具收到空 meta 或 toolId, 跳过");
            return;
        }
        index.put(meta.getToolId(), meta);
        log.debug("索引工具: toolId={}, name={}", meta.getToolId(), meta.getName());
    }

    /** 当前索引工具数 (供测试 / 监控使用)。 */
    public int size() {
        return index.size();
    }
}
