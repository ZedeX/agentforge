package com.agent.tool.engine.api;

import com.agent.tool.engine.sandbox.SandboxExecResult;
import com.agent.tool.engine.sandbox.SandboxInstance;
import com.agent.tool.engine.sandbox.SandboxSpec;

import java.util.List;
import java.util.Map;

/**
 * Sandbox borrower port (F8 R3 exec: container borrow + exec + recycle).
 *
 * <p>R3 tools execute in a disposable container. Lifecycle:
 * <ol>
 *   <li>{@link #borrow(SandboxSpec)} — acquire a container (warm pool or cold create)</li>
 *   <li>{@link #exec} — run a command inside, capture stdout/stderr/exitCode</li>
 *   <li>{@link #release(String)} — return to pool (reuse) or destroy</li>
 * </ol>
 * </p>
 *
 * <p>Legacy {@link #borrow()} / {@link #recycle(String)} retained for
 * backward compat with {@code ToolGatewayImpl} skeleton (T1) until T8
 * rewrite switches the gateway to the spec-based API.</p>
 */
public interface SandboxBorrower {

    // ============ T6 spec-based API ============

    /**
     * Borrow a sandbox container matching {@code spec}.
     *
     * <p>Acquires a concurrency permit (blocks up to borrow timeout),
     * then either reuses a warm pooled container with matching spec or
     * creates a new one.</p>
     *
     * @return a {@link SandboxInstance} carrying the containerId
     */
    SandboxInstance borrow(SandboxSpec spec);

    /**
     * Execute {@code command} inside the borrowed container.
     *
     * @param containerId target container id (from {@link SandboxInstance#getContainerId})
     * @param command     argv array, e.g. {@code ["sh","-c","echo hello"]}
     * @param env         extra env vars (may be empty)
     * @param timeoutMs   exec timeout; on expiry the container is killed
     * @return exec result with stdout/stderr/exitCode/duration
     */
    SandboxExecResult exec(String containerId, List<String> command,
                           Map<String, String> env, long timeoutMs);

    /**
     * Release a borrowed container back to the pool (reuse) or destroy it
     * (pool full / spec mismatch / expired).
     *
     * @param containerId container id to release
     */
    void release(String containerId);

    /**
     * Sweep idle pool for expired containers and destroy them.
     * Called by a {@code @Scheduled} task in production.
     *
     * @return number of containers destroyed
     */
    int cleanupExpired();

    // ============ Legacy API (T1 skeleton compat) ============

    /**
     * Borrow a sandbox with default spec. Returns the containerId string.
     *
     * @deprecated use {@link #borrow(SandboxSpec)} — kept for ToolGatewayImpl skeleton.
     */
    @Deprecated
    String borrow();

    /**
     * Recycle (destroy) a sandbox by id. Equivalent to {@link #release(String)}
     * with destroy semantics.
     *
     * @deprecated use {@link #release(String)} — kept for ToolGatewayImpl skeleton.
     */
    @Deprecated
    void recycle(String sandboxId);

    // ============ Monitoring ============

    /** Current count of active (borrowed, not yet released) containers. */
    int activeCount();
}
