package com.agent.tool.engine.sandbox;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sandbox container specification (doc 05-tool-engine §5.2).
 *
 * <p>Describes how a Docker sandbox container should be created:
 * image / cpu / memory / network / tmpfs / mounts / env / exec timeout.</p>
 *
 * <p>Two specs are considered equal when all fields match — used by
 * {@link SandboxPool#poll(SandboxSpec)} to reuse a warm container whose
 * spec matches the requested one.</p>
 */
public final class SandboxSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String image;
    private final double cpuCores;
    private final long memoryBytes;
    private final long tmpfsBytes;
    private final String networkMode;        // "none" | "bridge"
    private final Map<String, String> mounts; // containerPath -> hostPath (read-only bind)
    private final Map<String, String> env;    // env var name -> value
    private final long execTimeoutMs;

    private SandboxSpec(Builder b) {
        this.image = b.image;
        this.cpuCores = b.cpuCores;
        this.memoryBytes = b.memoryBytes;
        this.tmpfsBytes = b.tmpfsBytes;
        this.networkMode = b.networkMode;
        this.mounts = Collections.unmodifiableMap(new LinkedHashMap<>(b.mounts));
        this.env = Collections.unmodifiableMap(new LinkedHashMap<>(b.env));
        this.execTimeoutMs = b.execTimeoutMs;
    }

    public String getImage() { return image; }
    public double getCpuCores() { return cpuCores; }
    public long getMemoryBytes() { return memoryBytes; }
    public long getTmpfsBytes() { return tmpfsBytes; }
    public String getNetworkMode() { return networkMode; }
    public Map<String, String> getMounts() { return mounts; }
    public Map<String, String> getEnv() { return env; }
    public long getExecTimeoutMs() { return execTimeoutMs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SandboxSpec that)) return false;
        return Double.compare(that.cpuCores, cpuCores) == 0
                && memoryBytes == that.memoryBytes
                && tmpfsBytes == that.tmpfsBytes
                && execTimeoutMs == that.execTimeoutMs
                && Objects.equals(image, that.image)
                && Objects.equals(networkMode, that.networkMode)
                && Objects.equals(mounts, that.mounts)
                && Objects.equals(env, that.env);
    }

    @Override
    public int hashCode() {
        return Objects.hash(image, cpuCores, memoryBytes, tmpfsBytes,
                networkMode, mounts, env, execTimeoutMs);
    }

    /** Build a {@link SandboxSpec}. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String image = "agent-sandbox:latest";
        private double cpuCores = 1.0;
        private long memoryBytes = 536870912L;  // 512MB
        private long tmpfsBytes = 67108864L;    // 64MB
        private String networkMode = "none";
        private Map<String, String> mounts = new LinkedHashMap<>();
        private Map<String, String> env = new LinkedHashMap<>();
        private long execTimeoutMs = 60000L;

        public Builder image(String image) { this.image = image; return this; }
        public Builder cpuCores(double cpuCores) { this.cpuCores = cpuCores; return this; }
        public Builder memoryBytes(long memoryBytes) { this.memoryBytes = memoryBytes; return this; }
        public Builder tmpfsBytes(long tmpfsBytes) { this.tmpfsBytes = tmpfsBytes; return this; }
        public Builder networkMode(String networkMode) { this.networkMode = networkMode; return this; }
        public Builder mount(String containerPath, String hostPath) {
            this.mounts.put(containerPath, hostPath); return this;
        }
        public Builder env(String name, String value) {
            this.env.put(name, value); return this;
        }
        public Builder env(Map<String, String> env) {
            this.env.putAll(env); return this;
        }
        public Builder execTimeoutMs(long execTimeoutMs) { this.execTimeoutMs = execTimeoutMs; return this; }

        public SandboxSpec build() {
            return new SandboxSpec(this);
        }
    }
}
