package com.agent.memory.api.impl;

import com.agent.memory.api.EmbeddingClient;
import com.agent.memory.api.LongTermMemoryWriter;
import com.agent.memory.api.MemoryVectorStore;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 长期记忆写入实现（F12.D1: skip write on task failure, write on success）。
 *
 * <p>写入流程：
 * <ol>
 *   <li>若 memoryId 为空，生成 UUID</li>
 *   <li>调用 {@link EmbeddingClient} 对 content 向量化</li>
 *   <li>调用 {@link MemoryVectorStore#insert} 持久化</li>
 *   <li>返回 memoryId</li>
 * </ol>
 * skipWrite 仅记录日志，不实际写入。</p>
 *
 * @see LongTermMemoryWriter
 */
@Component
public class LongTermMemoryWriterImpl implements LongTermMemoryWriter {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryWriterImpl.class);

    private final MemoryVectorStore vectorStore;
    private final EmbeddingClient embeddingClient;

    public LongTermMemoryWriterImpl(MemoryVectorStore vectorStore, EmbeddingClient embeddingClient) {
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public String write(MemoryRecord record) {
        if (record == null) {
            log.warn("长期记忆写入失败：MemoryRecord 为 null");
            return null;
        }
        if (record.getMemoryId() == null || record.getMemoryId().isEmpty()) {
            record.setMemoryId(UUID.randomUUID().toString());
        }
        EmbeddingVector vector = embeddingClient.embed(record.getContent());
        vectorStore.insert(record, vector);
        log.info("长期记忆写入成功 memoryId={} dim={}",
                record.getMemoryId(), vector == null ? 0 : vector.getDim());
        return record.getMemoryId();
    }

    @Override
    public void skipWrite(String reason) {
        log.info("跳过长期记忆写入 reason={}", reason);
    }
}