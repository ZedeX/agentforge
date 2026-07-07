package com.agent.common.outbox;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;

/**
 * Outbox relay that polls PENDING messages and publishes them (S-04 Plan Phase 2).
 *
 * <p>Scheduled to run periodically, this component:
 * <ol>
 *   <li>Polls PENDING outbox messages (limited by batch size)</li>
 *   <li>Publishes each message via {@link OutboxPublisher}</li>
 *   <li>On success: marks SENT</li>
 *   <li>On failure: marks FAILED with exponential backoff, or DEAD if max retries exceeded</li>
 * </ol>
 */
@Slf4j
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final OutboxPublisher outboxPublisher;
    private final OutboxProperties properties;

    public OutboxRelay(OutboxRepository outboxRepository,
                       OutboxPublisher outboxPublisher,
                       OutboxProperties properties) {
        this.outboxRepository = outboxRepository;
        this.outboxPublisher = outboxPublisher;
        this.properties = properties;
    }

    /**
     * Relay pending messages. Called by @Scheduled or manually.
     */
    public void relayMessages() {
        if (!properties.isEnabled()) {
            return;
        }

        List<OutboxMessage> pending = outboxRepository.findPending(properties.getBatchSize());
        if (pending.isEmpty()) {
            return;
        }

        log.info("Outbox relay: processing {} PENDING messages", pending.size());

        for (OutboxMessage msg : pending) {
            try {
                boolean success = outboxPublisher.publish(msg.getTopic(), msg.getAggregateId(), msg.getPayload());
                if (success) {
                    outboxRepository.markSent(msg.getId());
                    log.debug("Outbox relay: marked SENT for id={}", msg.getId());
                } else {
                    handleFailure(msg);
                }
            } catch (Exception e) {
                log.error("Outbox relay: publish exception for id={}, err={}", msg.getId(), e.getMessage(), e);
                handleFailure(msg);
            }
        }
    }

    private void handleFailure(OutboxMessage msg) {
        int nextRetryCount = msg.getRetryCount() + 1;
        if (nextRetryCount >= properties.getMaxRetries()) {
            outboxRepository.markDead(msg.getId());
            log.warn("Outbox relay: marked DEAD for id={}, retryCount={}", msg.getId(), nextRetryCount);
        } else {
            long delayMs = (1L << msg.getRetryCount()) * properties.getInitialRetryDelayMs();
            Instant nextRetryAt = Instant.now().plusMillis(delayMs);
            outboxRepository.markFailed(msg.getId(), nextRetryAt);
            log.debug("Outbox relay: marked FAILED for id={}, nextRetryAt={}, retryCount={}",
                    msg.getId(), nextRetryAt, nextRetryCount);
        }
    }
}
