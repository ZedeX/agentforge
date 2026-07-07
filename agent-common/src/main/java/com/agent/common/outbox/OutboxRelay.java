package com.agent.common.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
 *
 * <p>Micrometer metrics (Phase 6 S-04 monitoring):
 * <ul>
 *   <li>{@code outbox_relay_published_total} — counter of successfully published messages</li>
 *   <li>{@code outbox_relay_failed_total} — counter of failed (FAILED + DEAD) messages</li>
 *   <li>{@code outbox_relay_latency_seconds} — timer per relay cycle</li>
 * </ul>
 */
@Slf4j
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final OutboxPublisher outboxPublisher;
    private final OutboxProperties properties;
    private final MeterRegistry meterRegistry;

    // Micrometer metrics (nullable for unit tests without MeterRegistry)
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Timer relayTimer;

    public OutboxRelay(OutboxRepository outboxRepository,
                       OutboxPublisher outboxPublisher,
                       OutboxProperties properties) {
        this(outboxRepository, outboxPublisher, properties, null);
    }

    public OutboxRelay(OutboxRepository outboxRepository,
                       OutboxPublisher outboxPublisher,
                       OutboxProperties properties,
                       MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.outboxPublisher = outboxPublisher;
        this.properties = properties;
        this.meterRegistry = meterRegistry;

        if (meterRegistry != null) {
            this.publishedCounter = Counter.builder("outbox_relay_published_total")
                    .description("Total number of outbox messages successfully published")
                    .register(meterRegistry);
            this.failedCounter = Counter.builder("outbox_relay_failed_total")
                    .description("Total number of outbox messages that failed to publish (FAILED + DEAD)")
                    .register(meterRegistry);
            this.relayTimer = Timer.builder("outbox_relay_latency_seconds")
                    .description("Time spent in each outbox relay cycle")
                    .register(meterRegistry);
        } else {
            this.publishedCounter = null;
            this.failedCounter = null;
            this.relayTimer = null;
        }
    }

    /**
     * Relay pending messages. Called by @Scheduled or manually.
     */
    public void relayMessages() {
        if (!properties.isEnabled()) {
            return;
        }

        long startNanos = System.nanoTime();
        try {
            doRelayMessages();
        } finally {
            if (relayTimer != null) {
                relayTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    private void doRelayMessages() {
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
                    if (publishedCounter != null) {
                        publishedCounter.increment();
                    }
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
        if (failedCounter != null) {
            failedCounter.increment();
        }
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
