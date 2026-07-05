package com.agent.tool.engine.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Warm sandbox container pool (doc 05-tool-engine §5.4).
 *
 * <p>Maintains a deque of idle {@link SandboxInstance}s plus a
 * {@link Semaphore} capping concurrent borrows at {@code maxConcurrent}.
 * Borrow flow: acquire permit → poll pool for a matching idle instance
 * → miss → create new. Release flow: if idle size &lt; poolSize and spec
 * matches → push back; else destroy.</p>
 *
 * <p>Thread-safe via {@link ConcurrentLinkedDeque} + {@link Semaphore}.</p>
 *
 * <p>Not a Spring bean — constructed by {@link DockerSandboxBorrower} with
 * poolSize / maxConcurrent from {@link com.agent.tool.engine.config.ToolEngineProperties.Sandbox}.</p>
 */
public class SandboxPool {

    private static final Logger log = LoggerFactory.getLogger(SandboxPool.class);

    /** Idle containers available for reuse. */
    private final Deque<SandboxInstance> idle = new ConcurrentLinkedDeque<>();
    /** Concurrency cap (maxConcurrent). */
    private final Semaphore permits;
    /** Max idle pool size (poolSize). */
    private final int poolSize;
    /** Max concurrent borrows. */
    private final int maxConcurrent;

    public SandboxPool(int poolSize, int maxConcurrent) {
        this.poolSize = poolSize;
        this.maxConcurrent = maxConcurrent;
        this.permits = new Semaphore(maxConcurrent, true);
    }

    /**
     * Acquire a borrow permit within {@code borrowTimeoutMs}.
     *
     * @return true if permit acquired; false if pool exhausted (timeout)
     */
    public boolean acquirePermit(long borrowTimeoutMs) {
        try {
            return permits.tryAcquire(borrowTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Release a borrow permit back to the semaphore. */
    public void releasePermit() {
        permits.release();
    }

    /**
     * Poll the pool for an idle instance whose spec matches {@code spec}.
     *
     * <p>Iterates the deque and removes the first matching instance.
     * Non-matching instances are put back. Returns empty when no match.</p>
     */
    public Optional<SandboxInstance> poll(SandboxSpec spec) {
        if (spec == null) {
            return Optional.ofNullable(idle.poll());
        }
        List<SandboxInstance> skipped = new ArrayList<>();
        SandboxInstance match = null;
        SandboxInstance cur;
        while ((cur = idle.poll()) != null) {
            if (match == null && spec.equals(cur.getSpec())) {
                match = cur;
            } else {
                skipped.add(cur);
            }
        }
        // Put back any we skipped (order is best-effort under concurrency)
        for (SandboxInstance s : skipped) {
            idle.push(s);
        }
        return Optional.ofNullable(match);
    }

    /**
     * Return an instance to the pool if there's room.
     *
     * <p>Calls {@link SandboxInstance#touch()} to reset the idle timer so the
     * returned container gets a full idle-timeout window before expiry.</p>
     *
     * @return true if accepted back into the pool; false if pool full (caller should destroy)
     */
    public boolean offer(SandboxInstance instance) {
        if (instance == null) {
            return false;
        }
        if (idle.size() >= poolSize) {
            return false;
        }
        instance.touch();
        idle.push(instance);
        return true;
    }

    /**
     * Offer an instance without resetting its {@code lastUsedAt}.
     *
     * <p>Test helper for sweep-expiry tests that need to inject a pre-aged
     * instance. Not for production use — callers should normally use
     * {@link #offer(SandboxInstance)} so the idle timer starts fresh.</p>
     */
    void offerRaw(SandboxInstance instance) {
        if (instance == null || idle.size() >= poolSize) {
            return;
        }
        idle.push(instance);
    }

    /** Current idle pool size. */
    public int idleSize() {
        return idle.size();
    }

    /** Number of permits currently acquired (= active borrows). */
    public int activeCount() {
        return maxConcurrent - permits.availablePermits();
    }

    /**
     * Sweep idle instances whose lastUsedAt is older than {@code idleTimeoutMs}
     * and return them for destruction.
     *
     * @return list of expired instances (caller destroys them)
     */
    public List<SandboxInstance> sweepExpired(long idleTimeoutMs) {
        List<SandboxInstance> expired = new ArrayList<>();
        Instant cutoff = Instant.now().minus(Duration.ofMillis(idleTimeoutMs));
        List<SandboxInstance> kept = new ArrayList<>();
        SandboxInstance cur;
        while ((cur = idle.poll()) != null) {
            if (cur.getLastUsedAt().isBefore(cutoff)) {
                expired.add(cur);
            } else {
                kept.add(cur);
            }
        }
        for (SandboxInstance s : kept) {
            idle.push(s);
        }
        log.debug("Swept expired idle sandboxes: expired={}, kept={}", expired.size(), kept.size());
        return expired;
    }

    /** Drain all idle instances (for @PreDestroy cleanup). Returns the drained list. */
    public List<SandboxInstance> drainAll() {
        List<SandboxInstance> all = new ArrayList<>();
        SandboxInstance cur;
        while ((cur = idle.poll()) != null) {
            all.add(cur);
        }
        return all;
    }
}
