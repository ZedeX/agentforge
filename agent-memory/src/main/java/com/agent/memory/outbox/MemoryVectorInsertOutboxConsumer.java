package com.agent.memory.outbox;

import com.agent.common.outbox.OutboxMessageHandler;
import com.agent.common.utils.JsonUtils;
import com.agent.memory.api.MemoryVectorStore;
import com.agent.memory.model.EmbeddingVector;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.VectorInsertPayload;
import com.agent.memory.repository.MemoryRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * S-04: Consumes outbox messages for the "memory.vector.insert" topic.
 *
 * <p>When a long-term memory write completes (JPA save succeeds), the
 * vector insertion is deferred to the outbox for eventual consistency.
 * This handler processes those messages by:</p>
 * <ol>
 *   <li>Deserializing the {@link VectorInsertPayload} from JSON</li>
 *   <li>Loading the {@link MemoryRecord} from the repository</li>
 *   <li>Calling {@link MemoryVectorStore#insert} with the record and embedding</li>
 * </ol>
 *
 * <p>If the vector store is temporarily unavailable, the consumer throws
 * an exception so the outbox framework will retry delivery. This ensures
 * that no memory record is left without its vector representation.</p>
 *
 * @see com.agent.memory.api.impl.LongTermMemoryWriterImpl
 */
@Component
public class MemoryVectorInsertOutboxConsumer implements OutboxMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MemoryVectorInsertOutboxConsumer.class);

    static final String TOPIC = "memory.vector.insert";

    private final MemoryRecordRepository repository;
    private final MemoryVectorStore vectorStore;

    public MemoryVectorInsertOutboxConsumer(MemoryRecordRepository repository,
                                            MemoryVectorStore vectorStore) {
        this.repository = repository;
        this.vectorStore = vectorStore;
    }

    @Override
    public void handle(String topic, String payload) {
        if (!TOPIC.equals(topic)) {
            log.warn("MemoryVectorInsertOutboxConsumer received unexpected topic: {}", topic);
            return;
        }

        try {
            VectorInsertPayload insertPayload = JsonUtils.fromJson(payload, VectorInsertPayload.class);
            Optional<MemoryRecord> recordOpt = repository.findByMemoryId(insertPayload.getMemoryId());

            if (recordOpt.isEmpty()) {
                log.error("Outbox vector insert: memory record not found, memoryId={}",
                        insertPayload.getMemoryId());
                throw new IllegalStateException(
                        "Memory record not found: " + insertPayload.getMemoryId());
            }

            MemoryRecord record = recordOpt.get();
            EmbeddingVector vector = new EmbeddingVector(insertPayload.getEmbeddingValues());
            vectorStore.insert(record, vector);

            log.info("Outbox vector insert succeeded: memoryId={}, dim={}",
                    insertPayload.getMemoryId(), insertPayload.getEmbeddingDim());
        } catch (Exception e) {
            log.error("Outbox vector insert failed: err={}", e.getMessage(), e);
            throw e; // Let OutboxConsumer catch and return false for retry
        }
    }
}
