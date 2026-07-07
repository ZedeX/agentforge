package com.agent.common.outbox;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * Idempotent outbox message consumer (S-04 Plan Phase 2).
 *
 * <p>Consumes outbox messages delivered via RocketMQ with idempotency guarantee:
 * <ol>
 *   <li>Check {@link ConsumeLogRepository#existsById(String)} — if found, skip (duplicate)</li>
 *   <li>Call {@link OutboxMessageHandler#handle(String, String)} to process</li>
 *   <li>Record in {@link ConsumeLogRepository#save(String)}</li>
 * </ol>
 * This mirrors the S-03 {@code event_consume_log} pattern.</p>
 */
@Slf4j
public class OutboxConsumer {

    private final ConsumeLogRepository consumeLogRepository;
    private final OutboxMessageHandler messageHandler;

    public OutboxConsumer(ConsumeLogRepository consumeLogRepository,
                          OutboxMessageHandler messageHandler) {
        this.consumeLogRepository = consumeLogRepository;
        this.messageHandler = messageHandler;
    }

    /**
     * Consume an outbox message with idempotency check.
     *
     * @param eventId event ID (= outbox_message.id, used as idempotency key)
     * @param topic   RocketMQ topic
     * @param payload message payload (JSON)
     * @return true if processed successfully, false if duplicate or handler failure
     */
    public boolean consume(String eventId, String topic, String payload) {
        // Idempotency check: skip if already consumed
        if (consumeLogRepository.existsById(eventId)) {
            log.debug("Outbox consumer: skipping duplicate eventId={}", eventId);
            return false;
        }

        try {
            // Process the message
            messageHandler.handle(topic, payload);

            // Record consumption (idempotency log)
            consumeLogRepository.save(eventId);
            log.debug("Outbox consumer: processed eventId={}, topic={}", eventId, topic);
            return true;
        } catch (Exception e) {
            log.error("Outbox consumer: handler failed for eventId={}, err={}", eventId, e.getMessage(), e);
            return false;
        }
    }
}
