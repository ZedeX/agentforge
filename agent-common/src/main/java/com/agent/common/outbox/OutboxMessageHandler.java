package com.agent.common.outbox;

/**
 * Handler for outbox message consumption (S-04 Plan Phase 2).
 *
 * <p>Implementations process the deserialized outbox message payload.
 * Each service that consumes outbox messages provides its own implementation.</p>
 */
public interface OutboxMessageHandler {

    /**
     * Handle a consumed outbox message.
     *
     * @param topic   the RocketMQ topic the message was published to
     * @param payload the message payload (JSON string)
     */
    void handle(String topic, String payload);
}
