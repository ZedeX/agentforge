package com.agent.common.outbox;

import lombok.Getter;
import lombok.Setter;

/**
 * Outbox relay configuration properties (S-04 Plan Phase 2).
 *
 * <p>Prefix: {@code outbox.relay}</p>
 */
@Getter
@Setter
public class OutboxProperties {

    /** Whether outbox relay is enabled. Default: true. */
    private boolean enabled = true;

    /** Batch size for polling PENDING messages. Default: 100. */
    private int batchSize = 100;

    /** Maximum retry attempts before marking DEAD. Default: 5. */
    private int maxRetries = 5;

    /** Initial retry delay in milliseconds (exponential backoff base). Default: 1000. */
    private long initialRetryDelayMs = 1000;

    /** Polling interval in milliseconds. Default: 5000. */
    private long pollIntervalMs = 5000;
}
