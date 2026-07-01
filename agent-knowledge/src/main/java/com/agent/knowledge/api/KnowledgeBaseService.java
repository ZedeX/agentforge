package com.agent.knowledge.api;

import com.agent.knowledge.enums.ChunkStrategyType;
import com.agent.knowledge.enums.DocumentType;
import com.agent.knowledge.model.IngestResult;
import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.model.SearchResult;

import java.util.List;

/**
 * JPA-backed knowledge base management service (Plan 08 T11).
 *
 * <p>Canonical KB CRUD + ingest/search orchestration backed by {@code KnowledgeBaseRepository}
 * (JPA). Distinct from the in-memory skeleton {@code KnowledgeService} which will be deprecated.</p>
 *
 * <p>Methods:</p>
 * <ul>
 *   <li>{@link #createBase} — 创建 KB（JPA 持久化，status=READY）</li>
 *   <li>{@link #ingest} — 委托 {@link DocumentIngestor}，KB 不存在抛 KB_NOT_FOUND</li>
 *   <li>{@link #search} — 委托 {@link com.agent.knowledge.api.KnowledgeRetriever}</li>
 *   <li>{@link #listBases} — 状态过滤列出 KB（空过滤 = 全部非 DELETED）</li>
 *   <li>{@link #deleteBase} — 软删（force=false 且有文档时抛 KB_IN_USE）</li>
 * </ul>
 */
public interface KnowledgeBaseService {

    /**
     * 创建知识库。
     *
     * @param kbId       知识库 id，null/empty 自动生成
     * @param name       名称（必填）
     * @param description 描述（可空）
     * @return 持久化后的 KnowledgeBase
     */
    KnowledgeBase createBase(String kbId, String name, String description);

    /**
     * 文档入库（委托 DocumentIngestor）。
     *
     * @return IngestResult；KB 不存在或已删除抛 KB_NOT_FOUND，入库失败抛 DOC_INGEST_FAILED
     */
    IngestResult ingest(String kbId, String docId, String name, String content,
                        DocumentType type, ChunkStrategyType strategy,
                        int maxTokens, int overlap);

    /**
     * 检索 chunks（委托 KnowledgeRetriever）。
     *
     * @param kbId      知识库 id
     * @param query     查询文本
     * @param topK      返回条数
     * @param enableMmr 是否 MMR 重排
     * @return 按分数降序的 SearchResult 列表
     */
    List<SearchResult> search(String kbId, String query, int topK, boolean enableMmr);

    /**
     * 列出知识库。
     *
     * @param statusFilter 状态过滤（null/empty = 全部非 DELETED），否则按 fromCode 解析
     * @return KnowledgeBase 列表
     */
    List<KnowledgeBase> listBases(String statusFilter);

    /**
     * 软删知识库。
     *
     * @param kbId  知识库 id
     * @param force true 忽略引用检查；false 且 docCount>0 时抛 KB_IN_USE
     * @return true 删除成功
     */
    boolean deleteBase(String kbId, boolean force);
}
