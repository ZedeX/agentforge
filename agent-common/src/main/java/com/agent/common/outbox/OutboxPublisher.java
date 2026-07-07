package com.agent.common.outbox;

/**
 * Outbox message publisher interface (S-04 Plan Phase 2).
 *
 * <p>Implementations publish outbox messages to a message broker
 * (e.g., RocketMQ). The relay calls this for each PENDING message.</p>
 */
public interface OutboxPublisher {

    /**
     * Publish a message to the specified topic.
     *
     * @param topic       RocketMQ topic
     * @param aggregateId business aggregate ID (used as message key for partitioning)
     * @param payload     message payload (JSON string)
     * @return true if publish succeeded, false if failed (relay will mark FAILED)
     */
    boolean publish(String topic, String aggregateId, String payload);
}
