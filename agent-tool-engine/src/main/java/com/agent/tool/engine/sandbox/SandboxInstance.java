package com.agent.tool.engine.sandbox;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A borrowed sandbox container instance (doc 05-tool-engine §5.3).
 *
 * <p>Tracks the Docker containerId, the spec it was created with, and
 * lifecycle timestamps used by {@link SandboxPool} for idle-expiry.</p>
 */
public final class SandboxInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String containerId;
    private final SandboxSpec spec;
    private final Instant createdAt;
    private volatile Instant lastUsedAt;

    public SandboxInstance(String containerId, SandboxSpec spec) {
        this(containerId, spec, Instant.now(), Instant.now());
    }

    public SandboxInstance(String containerId, SandboxSpec spec,
                           Instant createdAt, Instant lastUsedAt) {
        this.containerId = containerId;
        this.spec = spec;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
    }

    public String getContainerId() { return containerId; }
    public SandboxSpec getSpec() { return spec; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }

    /** Mark this instance as touched right before returning to the pool. */
    public void touch() {
        this.lastUsedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SandboxInstance that)) return false;
        return Objects.equals(containerId, that.containerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(containerId);
    }

    @Override
    public String toString() {
        return "SandboxInstance{containerId='" + containerId + "', spec=" + spec + '}';
    }
}
