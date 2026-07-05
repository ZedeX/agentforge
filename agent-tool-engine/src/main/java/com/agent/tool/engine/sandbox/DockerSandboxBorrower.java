package com.agent.tool.engine.sandbox;

import com.agent.tool.engine.api.SandboxBorrower;
import com.agent.tool.engine.config.ToolEngineProperties;
import com.agent.tool.engine.exception.ToolSandboxFailureException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Docker-based {@link SandboxBorrower} implementation (doc 05-tool-engine §5).
 *
 * <p>Active only when {@code tool.docker.enabled=true}. Maintains a warm pool
 * of containers ({@link SandboxPool}) plus an active (borrowed) map. Lifecycle:
 * <ol>
 *   <li>{@link #borrow(SandboxSpec)} — acquire permit, reuse pooled container
 *       with matching spec or create a new one (cpu/memory/network/tmpfs binds).</li>
 *   <li>{@link #exec} — execCreateCmd + execStartCmd with stdout/stderr capture;
 *       on timeout kill the container and return {@code timedOut=true}.</li>
 *   <li>{@link #release(String)} — return to pool (if room + spec match) or
 *       {@code removeContainerCmd(force=true)}.</li>
 *   <li>{@link #cleanupExpired()} — sweep pool for idle-expired containers and destroy.</li>
 * </ol>
 * </p>
 *
 * <p>Docker daemon errors are wrapped in {@link ToolSandboxFailureException}.
 * Pool exhaustion (semaphore timeout) throws {@link ToolSandboxFailureException}
 * with {@link ToolSandboxFailureException#CODE_POOL_EXHAUSTED}.</p>
 */
@Component
@ConditionalOnProperty(name = "tool.docker.enabled", havingValue = "true")
public class DockerSandboxBorrower implements SandboxBorrower {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxBorrower.class);

    private final DockerClient dockerClient;
    private final ToolEngineProperties properties;
    private final SandboxPool pool;
    private final long borrowTimeoutMs;

    /** Active (borrowed, not yet released) containers: containerId → instance. */
    private final Map<String, SandboxInstance> active = new ConcurrentHashMap<>();

    public DockerSandboxBorrower(DockerClient dockerClient, ToolEngineProperties properties) {
        this.dockerClient = dockerClient;
        this.properties = properties;
        ToolEngineProperties.Sandbox sb = properties.getSandbox();
        this.pool = new SandboxPool(sb.getPoolSize(), sb.getMaxConcurrent());
        this.borrowTimeoutMs = sb.getBorrowTimeoutMs();
    }

    // ============ Lifecycle ============

    /**
     * Best-effort warm pool pre-heat: create up to {@code poolSize} containers
     * with the default spec. Failures are logged and skipped (cold-start still works).
     */
    @PostConstruct
    public void init() {
        ToolEngineProperties.Sandbox sb = properties.getSandbox();
        if (!sb.isEnabled()) {
            log.info("[docker-sandbox] disabled by config (tool.sandbox.enabled=false), skip warm pool");
            return;
        }
        SandboxSpec defaultSpec = defaultSpec();
        int warmed = 0;
        for (int i = 0; i < sb.getPoolSize(); i++) {
            try {
                SandboxInstance instance = createContainer(defaultSpec);
                if (pool.offer(instance)) {
                    warmed++;
                } else {
                    // Pool full (race with another init path) — destroy the extra.
                    destroyContainer(instance.getContainerId());
                }
            } catch (RuntimeException e) {
                log.warn("[docker-sandbox] warm-pool create #{} failed: {}", i + 1, e.getMessage());
            }
        }
        log.info("[docker-sandbox] warm pool pre-heated: {}/{}", warmed, sb.getPoolSize());
    }

    /** Destroy all idle + active containers on shutdown. */
    @PreDestroy
    public void destroy() {
        List<SandboxInstance> idleAll = pool.drainAll();
        int destroyed = 0;
        for (SandboxInstance instance : idleAll) {
            try {
                destroyContainer(instance.getContainerId());
                destroyed++;
            } catch (RuntimeException e) {
                log.warn("[docker-sandbox] destroy idle {} on shutdown failed: {}",
                        instance.getContainerId(), e.getMessage());
            }
        }
        // Active containers are still in use by callers; attempt forceful cleanup.
        for (String containerId : new ArrayList<>(active.keySet())) {
            try {
                destroyContainer(containerId);
                destroyed++;
            } catch (RuntimeException e) {
                log.warn("[docker-sandbox] destroy active {} on shutdown failed: {}",
                        containerId, e.getMessage());
            }
        }
        log.info("[docker-sandbox] shutdown cleanup destroyed {} containers", destroyed);
    }

    // ============ T6 API ============

    @Override
    public SandboxInstance borrow(SandboxSpec spec) {
        SandboxSpec effective = spec == null ? defaultSpec() : spec;
        if (!pool.acquirePermit(borrowTimeoutMs)) {
            throw new ToolSandboxFailureException(
                    ToolSandboxFailureException.CODE_POOL_EXHAUSTED,
                    "sandbox pool exhausted: no permit acquired within " + borrowTimeoutMs + "ms");
        }
        // Try warm pool first (permit already acquired).
        SandboxInstance instance = pool.poll(effective).orElse(null);
        if (instance == null) {
            try {
                instance = createContainer(effective);
            } catch (RuntimeException e) {
                pool.releasePermit();
                throw new ToolSandboxFailureException(
                        "create container failed for spec=" + effective, e);
            }
        } else {
            log.debug("[docker-sandbox] reuse warm container: {}", instance.getContainerId());
        }
        active.put(instance.getContainerId(), instance);
        return instance;
    }

    @Override
    public SandboxExecResult exec(String containerId, List<String> command,
                                  Map<String, String> env, long timeoutMs) {
        if (containerId == null || containerId.isBlank()) {
            return new SandboxExecResult("", "containerId is null/blank", 127, 0, false);
        }
        long start = System.currentTimeMillis();
        String execId;
        try {
            ExecCreateCmdResponse execResp = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(command.toArray(new String[0]))
                    .withEnv(toEnvList(env))
                    .exec();
            execId = execResp.getId();
        } catch (RuntimeException e) {
            long dur = System.currentTimeMillis() - start;
            log.warn("[docker-sandbox] execCreate failed: container={}, err={}", containerId, e.getMessage());
            return new SandboxExecResult("", "execCreate failed: " + e.getMessage(),
                    127, dur, false);
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ExecStartResultCallback callback = new ExecStartResultCallback(stdout, stderr);
        try {
            dockerClient.execStartCmd(execId).exec(callback);
        } catch (RuntimeException e) {
            long dur = System.currentTimeMillis() - start;
            log.warn("[docker-sandbox] execStart failed: exec={}, err={}", execId, e.getMessage());
            return new SandboxExecResult(stdout.toString(StandardCharsets.UTF_8),
                    "execStart failed: " + e.getMessage(), 127, dur, false);
        }

        boolean completed;
        try {
            completed = callback.awaitCompletion(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long dur = System.currentTimeMillis() - start;
            return new SandboxExecResult(stdout.toString(StandardCharsets.UTF_8),
                    "interrupted", -1, dur, true);
        }
        long duration = System.currentTimeMillis() - start;

        if (!completed) {
            // Timeout — kill the container to halt any lingering process.
            log.warn("[docker-sandbox] exec timeout: container={}, exec={}, timeoutMs={}",
                    containerId, execId, timeoutMs);
            try {
                dockerClient.killContainerCmd(containerId).exec();
            } catch (RuntimeException e) {
                log.warn("[docker-sandbox] killContainer after timeout failed: {}", e.getMessage());
            }
            return new SandboxExecResult(stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8), -1, duration, true);
        }

        Integer exitCode = null;
        try {
            InspectExecResponse inspect = dockerClient.inspectExecCmd(execId).exec();
            exitCode = inspect.getExitCode();
        } catch (RuntimeException e) {
            log.warn("[docker-sandbox] inspectExec failed: exec={}, err={}", execId, e.getMessage());
        }
        int code = exitCode == null ? -1 : exitCode;
        return new SandboxExecResult(stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8), code, duration, false);
    }

    @Override
    public void release(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return;
        }
        SandboxInstance instance = active.remove(containerId);
        if (instance == null) {
            log.debug("[docker-sandbox] release: containerId not in active map (already released?): {}", containerId);
            // Still attempt destroy in case it's an orphan.
            try {
                destroyContainer(containerId);
            } catch (RuntimeException e) {
                log.debug("[docker-sandbox] orphan destroy failed: {}", e.getMessage());
            }
            pool.releasePermit();
            return;
        }
        // Return to pool if room; otherwise destroy.
        if (pool.offer(instance)) {
            log.debug("[docker-sandbox] release: returned to pool: {}", containerId);
        } else {
            try {
                destroyContainer(containerId);
                log.debug("[docker-sandbox] release: pool full, destroyed: {}", containerId);
            } catch (RuntimeException e) {
                log.warn("[docker-sandbox] release destroy failed: {}", e.getMessage());
            }
        }
        pool.releasePermit();
    }

    /** Sweep idle pool for expired containers and destroy them. */
    @Scheduled(fixedDelay = 60_000L)
    @Override
    public int cleanupExpired() {
        List<SandboxInstance> expired = pool.sweepExpired(properties.getSandbox().getIdleTimeoutMs());
        int destroyed = 0;
        for (SandboxInstance instance : expired) {
            try {
                destroyContainer(instance.getContainerId());
                destroyed++;
            } catch (RuntimeException e) {
                log.warn("[docker-sandbox] cleanup destroy failed: {}, err={}",
                        instance.getContainerId(), e.getMessage());
            }
        }
        if (destroyed > 0) {
            log.info("[docker-sandbox] cleanupExpired destroyed {} idle containers", destroyed);
        }
        return destroyed;
    }

    // ============ Legacy API ============

    @Override
    @Deprecated
    public String borrow() {
        return borrow(defaultSpec()).getContainerId();
    }

    @Override
    @Deprecated
    public void recycle(String sandboxId) {
        release(sandboxId);
    }

    @Override
    public int activeCount() {
        return pool.activeCount();
    }

    // ============ Helpers ============

    /** Build the default spec from {@link ToolEngineProperties.Sandbox}. */
    private SandboxSpec defaultSpec() {
        ToolEngineProperties.Sandbox sb = properties.getSandbox();
        return SandboxSpec.builder()
                .image(sb.getImage())
                .cpuCores(sb.getCpuCores())
                .memoryBytes(sb.getMemoryBytes())
                .tmpfsBytes(sb.getTmpfsBytes())
                .networkMode("none")
                .execTimeoutMs(sb.getExecTimeoutMs())
                .build();
    }

    /** Create + start a Docker container per {@code spec}. */
    private SandboxInstance createContainer(SandboxSpec spec) {
        HostConfig hostConfig = buildHostConfig(spec);
        CreateContainerResponse resp;
        try {
            resp = dockerClient.createContainerCmd(spec.getImage())
                    .withHostConfig(hostConfig)
                    .withCmd("sleep", "infinity")
                    .withEnv(toEnvList(spec.getEnv()))
                    .exec();
        } catch (RuntimeException e) {
            throw new ToolSandboxFailureException("createContainerCmd failed for image=" + spec.getImage(), e);
        }
        String containerId = resp.getId();
        try {
            dockerClient.startContainerCmd(containerId).exec();
        } catch (RuntimeException e) {
            // Best-effort cleanup of the half-created container.
            try {
                dockerClient.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
            } catch (RuntimeException re) {
                log.debug("[docker-sandbox] cleanup after startContainer failure failed: {}", re.getMessage());
            }
            throw new ToolSandboxFailureException("startContainerCmd failed for " + containerId, e);
        }
        log.debug("[docker-sandbox] created + started container: {}", containerId);
        return new SandboxInstance(containerId, spec);
    }

    /** Build HostConfig with cpu / memory / network / tmpfs / binds per spec. */
    private HostConfig buildHostConfig(SandboxSpec spec) {
        // Docker expects nanocpus (1 CPU = 1e9 nanoCPUs).
        long nanoCpus = (long) (spec.getCpuCores() * 1_000_000_000L);
        HostConfig hostConfig = new HostConfig()
                .withNanoCPUs(nanoCpus)
                .withMemory(spec.getMemoryBytes())
                .withNetworkMode(spec.getNetworkMode());
        if (spec.getTmpfsBytes() > 0) {
            Map<String, String> tmpfs = new LinkedHashMap<>();
            tmpfs.put("/tmp", "size=" + spec.getTmpfsBytes());
            hostConfig.withTmpFs(tmpfs);
        }
        if (spec.getMounts() != null && !spec.getMounts().isEmpty()) {
            List<Bind> binds = new ArrayList<>();
            for (Map.Entry<String, String> e : spec.getMounts().entrySet()) {
                // hostPath -> containerPath (read-only)
                binds.add(new Bind(e.getValue(), new Volume(e.getKey())));
            }
            hostConfig.withBinds(binds.toArray(new Bind[0]));
        }
        return hostConfig;
    }

    /** Force-remove a container (and its volumes). */
    private void destroyContainer(String containerId) {
        dockerClient.removeContainerCmd(containerId)
                .withForce(true)
                .withRemoveVolumes(true)
                .exec();
    }

    /** Convert env Map → "KEY=VALUE" list (docker-java expects env as List<String>). */
    private List<String> toEnvList(Map<String, String> env) {
        List<String> list = new ArrayList<>();
        if (env == null || env.isEmpty()) {
            return list;
        }
        for (Map.Entry<String, String> e : env.entrySet()) {
            list.add(e.getKey() + "=" + (e.getValue() == null ? "" : e.getValue()));
        }
        return list;
    }

    // ============ Test helpers (package-private) ============

    /** Test helper: directly add an instance to the idle pool (skips Docker create). */
    void offerToPool(SandboxInstance instance) {
        pool.offer(instance);
    }

    /** Test helper: add to pool without touching (for sweep-expiry tests). */
    void offerToPoolRaw(SandboxInstance instance) {
        pool.offerRaw(instance);
    }

    /** Test helper: current idle pool size. */
    int idlePoolSize() {
        return pool.idleSize();
    }
}
