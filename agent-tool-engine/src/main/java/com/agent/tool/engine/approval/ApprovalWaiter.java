package com.agent.tool.engine.approval;

import com.agent.tool.engine.enums.ApprovalDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Approval await coordinator (doc 05-tool-engine §4.3 / §4.4).
 *
 * <p>Maintains a registry of {@link CompletableFuture} per approvalId so that
 * {@code ApprovalStore.await(approvalId, timeout)} can block until the
 * approver decides (via {@link #notify(String, ApprovalDecision)}) or the
 * SLA elapses.</p>
 *
 * <p>This is the in-process implementation. The production deployment can
 * swap in a Redis pub/sub backed implementation so awaiting works across
 * multiple tool-engine replicas (the approver may hit a different instance
 * than the one that called submit).</p>
 */
@Component
public class ApprovalWaiter {

    private static final Logger log = LoggerFactory.getLogger(ApprovalWaiter.class);

    /** Pending awaits: approvalId -> future holding the eventual decision. */
    private final ConcurrentHashMap<String, CompletableFuture<ApprovalDecision>> pending =
            new ConcurrentHashMap<>();

    /**
     * Register (or reuse) a future for the given approvalId.
     *
     * <p>Uses {@code computeIfAbsent} so concurrent submit + await callers
     * see the same future.</p>
     */
    public CompletableFuture<ApprovalDecision> register(String approvalId) {
        return pending.computeIfAbsent(approvalId, k -> new CompletableFuture<>());
    }

    /**
     * Block until the approver decides or timeout elapses.
     *
     * @return decision; {@link ApprovalDecision#TIMEOUT} if SLA elapses
     *         without a decision
     */
    public ApprovalDecision await(String approvalId, Duration timeout) {
        if (approvalId == null || timeout == null) {
            return ApprovalDecision.TIMEOUT;
        }
        CompletableFuture<ApprovalDecision> future = register(approvalId);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            log.debug("审批等待超时: approvalId={}, timeout={}ms", approvalId, timeout.toMillis());
            return ApprovalDecision.TIMEOUT;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("审批等待被中断: approvalId={}", approvalId);
            return ApprovalDecision.TIMEOUT;
        } catch (ExecutionException ee) {
            log.warn("审批等待异常: approvalId={}, cause={}", approvalId, ee.getMessage());
            return ApprovalDecision.REJECTED;
        } finally {
            pending.remove(approvalId);
        }
    }

    /**
     * Notify all waiters of the decision for the given approvalId.
     *
     * <p>Idempotent: if no waiter is registered (e.g. decision arrived
     * before await was called), the decision is recorded into a fresh
     * future so a subsequent await returns immediately.</p>
     */
    public void notify(String approvalId, ApprovalDecision decision) {
        if (approvalId == null || decision == null) {
            return;
        }
        CompletableFuture<ApprovalDecision> future = register(approvalId);
        future.complete(decision);
        log.debug("审批决策通知: approvalId={}, decision={}", approvalId, decision);
    }

    /** Peek the current registered decision (for tests / monitoring). */
    public Optional<ApprovalDecision> peek(String approvalId) {
        CompletableFuture<ApprovalDecision> future = pending.get(approvalId);
        if (future == null || !future.isDone()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(future.getNow(null));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Number of pending awaits (for tests / monitoring). */
    public int pendingCount() {
        return pending.size();
    }

    /** Remove a registration (cleanup on timeout / cancel). */
    public void forget(String approvalId) {
        pending.remove(approvalId);
    }
}
