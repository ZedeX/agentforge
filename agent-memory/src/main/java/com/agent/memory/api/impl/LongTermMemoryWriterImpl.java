package com.agent.memory.api.impl;

import com.agent.common.outbox.OutboxMessage;
import com.agent.common.outbox.OutboxRepository;
import com.agent.common.utils.JsonUtils;
import com.agent.memory.api.EmbeddingClient;
import com.agent.memory.api.ImportanceScorer;
import com.agent.memory.api.LongTermMemoryWriter;
import com.agent.memory.api.MemoryVectorStore;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.VectorInsertPayload;
import com.agent.memory.repository.MemoryRecordRepository;
import com.agent.memory.scorer.ImportanceResult;
import com.agent.memory.scorer.ScoringContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

/**
 * 长期记忆写入实现（F12.D1: skip write on task failure, write on success + Plan 03 T10 完整流程）。
 *
 * <p>写入流程（T10 扩展 + S-04 Outbox，当 scorer/repository 可用时走完整流程）：
 * <ol>
 *   <li>生成 memoryId（若为空）</li>
 *   <li>计算 contentHash（SHA-256，若为空）</li>
 *   <li>Dedup 检查：{@code repository.findByTenantIdAndContentHash} → 重复则返回已有 memoryId</li>
 *   <li>Importance 评分：{@link ImportanceScorer#score} → 写入 importanceScore / importanceLevel</li>
 *   <li>调用 {@link EmbeddingClient} 对 content 向量化</li>
 *   <li>同步：{@code repository.save(record)}（status=ACTIVE）</li>
 *   <li>S-04 Outbox：写入 outbox 消息（topic=memory.vector.insert）保证向量持久化最终一致</li>
 *   <li>返回 memoryId</li>
 * </ol>
 *
 * <p>向后兼容：旧构造器（2 参数）传 null 给 scorer/repository/outboxRepository，
 * 走简化流程（仅 embed + insert vector），保留 F12 骨架行为。
 * 生产构造器由 Spring @Autowired 注入全参。</p>
 *
 * @see LongTermMemoryWriter
 */
@Component
public class LongTermMemoryWriterImpl implements LongTermMemoryWriter {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryWriterImpl.class);

    /** S-04 Outbox topic for vector insertion. */
    static final String OUTBOX_TOPIC_VECTOR_INSERT = "memory.vector.insert";

    private final MemoryVectorStore vectorStore;
    private final EmbeddingClient embeddingClient;
    /** 可选：为 null 时跳过评分（向后兼容 F12 骨架）。 */
    private final ImportanceScorer scorer;
    /** 可选：为 null 时跳过 DB 持久化 + dedup（向后兼容 F12 骨架）。 */
    private final MemoryRecordRepository repository;
    /** 可选：为 null 时直接调用 vectorStore.insert（向后兼容）。 */
    private final OutboxRepository outboxRepository;

    /**
     * 旧构造器（F12 骨架向后兼容）：仅 embed + insert vector，无 dedup / score / DB save / outbox。
     */
    public LongTermMemoryWriterImpl(MemoryVectorStore vectorStore, EmbeddingClient embeddingClient) {
        this(vectorStore, embeddingClient, null, null, null);
    }

    /**
     * T10 全参构造器（无 outbox）：走完整写入流程（dedup + score + save DB + insert vector）。
     * 保留用于旧测试兼容。
     */
    public LongTermMemoryWriterImpl(MemoryVectorStore vectorStore, EmbeddingClient embeddingClient,
                                     ImportanceScorer scorer, MemoryRecordRepository repository) {
        this(vectorStore, embeddingClient, scorer, repository, null);
    }

    /**
     * S-04 全参构造器：走完整写入流程（dedup + score + save DB + outbox vector insert）。
     */
    public LongTermMemoryWriterImpl(MemoryVectorStore vectorStore, EmbeddingClient embeddingClient,
                                     ImportanceScorer scorer, MemoryRecordRepository repository,
                                     OutboxRepository outboxRepository) {
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
        this.scorer = scorer;
        this.repository = repository;
        this.outboxRepository = outboxRepository;
    }

    @Override
    public String write(MemoryRecord record) {
        if (record == null) {
            log.warn("长期记忆写入失败：MemoryRecord 为 null");
            return null;
        }
        // 1. 生成 memoryId
        if (record.getMemoryId() == null || record.getMemoryId().isEmpty()) {
            record.setMemoryId(UUID.randomUUID().toString());
        }
        // 2. 计算 contentHash（若为空）
        if (record.getContentHash() == null || record.getContentHash().isEmpty()) {
            record.setContentHash(sha256(record.getContent()));
        }
        // 3. Dedup 检查（若 repository 可用）
        if (repository != null && record.getTenantId() != null) {
            Optional<MemoryRecord> existing = repository
                    .findByTenantIdAndContentHash(record.getTenantId(), record.getContentHash())
                    .stream().findFirst();
            if (existing.isPresent()) {
                log.info("长期记忆写入被去重合并 memoryId={} existingId={}",
                        record.getMemoryId(), existing.get().getMemoryId());
                return existing.get().getMemoryId();
            }
        }
        // 4. Importance 评分（若 scorer 可用）
        if (scorer != null) {
            ImportanceResult result = scorer.score(record, ScoringContext.empty());
            record.setImportanceScore(result.getScore());
            record.setImportanceLevel(result.getLevel());
            log.debug("记忆评分完成 memoryId={} score={} level={}",
                    record.getMemoryId(), result.getScore(), result.getLevel());
        }
        // 5. 向量化
        EmbeddingVector vector = embeddingClient.embed(record.getContent());
        // 6. DB 持久化（若 repository 可用）
        if (repository != null) {
            repository.save(record);
        }
        // 7. S-04: 向量存储（通过 outbox 保证最终一致，或直接调用向后兼容）
        insertVector(record, vector);
        log.info("长期记忆写入成功 memoryId={} dim={} score={}",
                record.getMemoryId(),
                vector == null ? 0 : vector.getDim(),
                record.getImportanceScore());
        return record.getMemoryId();
    }

    /**
     * S-04: Insert vector via outbox (preferred) or direct call (fallback).
     *
     * <p>If outboxRepository is available, writes an outbox message for
     * eventual-consistent vector insertion. Otherwise falls back to
     * direct {@link MemoryVectorStore#insert} for backward compatibility.</p>
     */
    private void insertVector(MemoryRecord record, EmbeddingVector vector) {
        if (outboxRepository != null) {
            // S-04: Write to outbox for reliable vector insertion
            try {
                VectorInsertPayload payload = new VectorInsertPayload(
                        record.getMemoryId(),
                        record.getTenantId(),
                        vector != null ? vector.getValues() : null,
                        vector != null ? vector.getDim() : 0
                );
                OutboxMessage msg = new OutboxMessage();
                msg.setAggregateId(record.getMemoryId());
                msg.setTopic(OUTBOX_TOPIC_VECTOR_INSERT);
                msg.setPayload(JsonUtils.toJson(payload));
                outboxRepository.save(msg);
                log.debug("Outbox vector insert written: memoryId={}", record.getMemoryId());
            } catch (Exception outboxEx) {
                // Outbox write failure: fall back to direct insert to avoid data loss
                log.error("Outbox 写入失败, 降级为直接向量插入: memoryId={}, err={}",
                        record.getMemoryId(), outboxEx.getMessage(), outboxEx);
                vectorStore.insert(record, vector);
            }
        } else {
            // No outbox repository (unit test / F12 骨架) → direct insert
            vectorStore.insert(record, vector);
        }
    }

    @Override
    public void skipWrite(String reason) {
        log.info("跳过长期记忆写入 reason={}", reason);
    }

    /** 计算 SHA-256 hash（hex 字符串）。 */
    private static String sha256(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
