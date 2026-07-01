package com.agent.knowledge.api.impl;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.knowledge.api.DocumentIngestor;
import com.agent.knowledge.api.KnowledgeBaseService;
import com.agent.knowledge.api.KnowledgeRetriever;
import com.agent.knowledge.enums.ChunkStrategyType;
import com.agent.knowledge.enums.DocumentType;
import com.agent.knowledge.enums.KnowledgeStatus;
import com.agent.knowledge.model.IngestResult;
import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.model.KnowledgeQuery;
import com.agent.knowledge.model.SearchResult;
import com.agent.knowledge.repository.KnowledgeBaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA-backed {@link KnowledgeBaseService} implementation (Plan 08 T11).
 *
 * <p>编排 {@link KnowledgeBaseRepository}（KB CRUD）+ {@link DocumentIngestor}（入库）+
 * {@link KnowledgeRetriever}（检索）。所有写操作 @Transactional 保证原子性。</p>
 *
 * <p>异常策略：KB 不存在/已删除 → {@link ErrorCode#KB_NOT_FOUND}；KB 有文档且非强制删除 →
 * {@link ErrorCode#KB_IN_USE}；入库失败 → {@link ErrorCode#DOC_INGEST_FAILED}。</p>
 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final double DEFAULT_MMR_LAMBDA = 0.5;
    private static final int DEFAULT_TOP_K = 5;

    private final KnowledgeBaseRepository kbRepository;
    private final DocumentIngestor documentIngestor;
    private final KnowledgeRetriever knowledgeRetriever;

    public KnowledgeBaseServiceImpl(KnowledgeBaseRepository kbRepository,
                                    DocumentIngestor documentIngestor,
                                    KnowledgeRetriever knowledgeRetriever) {
        this.kbRepository = kbRepository;
        this.documentIngestor = documentIngestor;
        this.knowledgeRetriever = knowledgeRetriever;
    }

    @Override
    @Transactional
    public KnowledgeBase createBase(String kbId, String name, String description) {
        if (name == null || name.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "知识库名称不能为空");
        }
        if (kbId == null || kbId.isEmpty()) {
            kbId = "kb-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (kbRepository.existsByKbId(kbId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "知识库已存在: " + kbId);
        }
        KnowledgeBase kb = new KnowledgeBase(kbId, name);
        kb.setDescription(description);
        kb.setStatus(KnowledgeStatus.READY);
        return kbRepository.save(kb);
    }

    @Override
    @Transactional
    public IngestResult ingest(String kbId, String docId, String name, String content,
                               DocumentType type, ChunkStrategyType strategy,
                               int maxTokens, int overlap) {
        KnowledgeBase kb = loadKbOrThrow(kbId);
        IngestResult result = documentIngestor.ingestDocument(kbId, docId, name, content,
                type, strategy, maxTokens, overlap);
        if (!result.isSuccess()) {
            throw new BusinessException(ErrorCode.DOC_INGEST_FAILED, result.getMessage());
        }
        return result;
    }

    @Override
    public List<SearchResult> search(String kbId, String query, int topK, boolean enableMmr) {
        if (!kbRepository.existsByKbId(kbId)) {
            throw new BusinessException(ErrorCode.KB_NOT_FOUND, "知识库不存在: " + kbId);
        }
        int effectiveTopK = topK > 0 ? topK : DEFAULT_TOP_K;
        KnowledgeQuery q = new KnowledgeQuery(kbId, query, effectiveTopK, enableMmr, DEFAULT_MMR_LAMBDA);
        return knowledgeRetriever.search(q);
    }

    @Override
    public List<KnowledgeBase> listBases(String statusFilter) {
        if (statusFilter == null || statusFilter.isEmpty()) {
            // 空过滤：返回全部非 DELETED（按 createdAt 降序）
            return kbRepository.findAll().stream()
                    .filter(kb -> kb.getStatus() != KnowledgeStatus.DELETED)
                    .collect(Collectors.toList());
        }
        KnowledgeStatus status = KnowledgeStatus.fromCode(statusFilter);
        return kbRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Override
    @Transactional
    public boolean deleteBase(String kbId, boolean force) {
        KnowledgeBase kb = loadKbOrThrow(kbId);
        if (!force && kb.getDocCount() > 0) {
            throw new BusinessException(ErrorCode.KB_IN_USE,
                    "知识库含 " + kb.getDocCount() + " 个文档，需 force=true 强制删除");
        }
        kb.setStatus(KnowledgeStatus.DELETED);
        kbRepository.save(kb);
        return true;
    }

    /**
     * 加载 KB，不存在或已删除抛 KB_NOT_FOUND。
     */
    private KnowledgeBase loadKbOrThrow(String kbId) {
        if (kbId == null || kbId.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "知识库 id 不能为空");
        }
        KnowledgeBase kb = kbRepository.findByKbId(kbId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KB_NOT_FOUND, "知识库不存在: " + kbId));
        if (kb.getStatus() == KnowledgeStatus.DELETED) {
            throw new BusinessException(ErrorCode.KB_NOT_FOUND, "知识库已删除: " + kbId);
        }
        return kb;
    }
}
