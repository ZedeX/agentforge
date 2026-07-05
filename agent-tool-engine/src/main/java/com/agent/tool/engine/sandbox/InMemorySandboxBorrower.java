package com.agent.tool.engine.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory {@link com.agent.tool.engine.api.SandboxBorrower} fallback.
 *
 * <p>Active when {@code tool.docker.enabled=false} (default, including tests).
 * Simulates the borrow/exec/release lifecycle with an in-process map —
 * no real Docker daemon required. Useful for unit tests and local dev.</p>
 *
 * <p>{@code exec} returns a canned stdout echoing the command, so the
 * gateway skeleton (T1) can exercise its full pipeline without Docker.</p>
 */
@Component
@ConditionalOnProperty(name = "tool.docker.enabled", havingValue = "false", matchIfMissing = true)
public class InMemorySandboxBorrower implements com.agent.tool.engine.api.SandboxBorrower {

    private static final Logger log = LoggerFactory.getLogger(InMemorySandboxBorrower.class);

    /** Active sandboxes: containerId → SandboxInstance. */
    private final Map<String, SandboxInstance> active = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    @Override
    public SandboxInstance borrow(SandboxSpec spec) {
        String containerId = "sb-" + idSeq.incrementAndGet();
        SandboxInstance instance = new SandboxInstance(containerId, spec);
        active.put(containerId, instance);
        log.debug("[in-memory] borrow: {}", containerId);
        return instance;
    }

    @Override
    public SandboxExecResult exec(String containerId, List<String> command,
                                  Map<String, String> env, long timeoutMs) {
        if (containerId == null || !active.containsKey(containerId)) {
            return new SandboxExecResult("", "container not found: " + containerId,
                    127, 0, false);
        }
        long start = System.currentTimeMillis();
        String cmd = String.join(" ", command);
        String stdout = "executed:" + containerId + ":" + cmd + "\n";
        long duration = System.currentTimeMillis() - start;
        log.debug("[in-memory] exec: container={}, cmd={}", containerId, cmd);
        return new SandboxExecResult(stdout, "", 0, duration, false);
    }

    @Override
    public void release(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return;
        }
        SandboxInstance removed = active.remove(containerId);
        if (removed != null) {
            log.debug("[in-memory] release: {}", containerId);
        }
    }

    @Override
    public int cleanupExpired() {
        // In-memory has no idle pool — nothing to sweep.
        return 0;
    }

    // ============ Legacy API ============

    @Override
    public String borrow() {
        return borrow(SandboxSpec.builder().build()).getContainerId();
    }

    @Override
    public void recycle(String sandboxId) {
        release(sandboxId);
    }

    @Override
    public int activeCount() {
        return active.size();
    }

    /** Test helper: is the given containerId currently borrowed? */
    public boolean isActive(String containerId) {
        return containerId != null && active.containsKey(containerId);
    }
}
