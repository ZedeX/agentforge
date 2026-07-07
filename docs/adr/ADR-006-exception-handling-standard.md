# ADR-006: Exception Handling Standard

> Status: Approved  
> Date: 2026-07-07  
> Deciders: Platform Team  
> Context: S-12 audit finding — exception swallowing is endemic across the platform

---

## Context

The red-blue team audit (2026-07-07) identified S-12: exceptions are caught and swallowed across the platform. Specific patterns:

1. **Silent catch+log**: `catch (Exception e) { log.error("...", e.getMessage()); }` — no rethrow, no compensation, no annotation
2. **Missing logs in GrpcExceptionAdvice**: 12 of 13 implementations had zero logging, making gRPC errors invisible in server logs
3. **Over-broad catch**: `catch (Throwable)` in gRPC services catches `Error` (OOM, StackOverflow) that should crash the pod for K8s auto-restart
4. **Business logic swallow**: `ToolCallAuditorImpl.audit()` caught JPA exceptions internally, preventing upstream compensation from triggering

This ADR establishes a platform-wide standard to eliminate these patterns.

---

## Decision

### Rule 1: No silent catch+log

Every `catch` block must do **exactly one** of:

| Action | When to use | Example |
|--------|------------|---------|
| **Rethrow** | The caller should handle the error | `catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL, "...", e); }` |
| **Outbox compensation** | Cross-service write that must eventually succeed | `catch (Exception e) { outboxRepository.save(msg); }` |
| **Explicit annotation** | Intentionally ignored with documented reason | `catch (Exception e) { /* ADR-006: @Scheduled must not throw; next run retries */ log.error("...", e); }` |

**Anti-pattern** (FORBIDDEN):
```java
// BAD: swallowed with no rethrow, no compensation, no annotation
catch (Exception e) {
    log.error("Something failed: {}", e.getMessage());
}
```

**Compliant patterns**:
```java
// GOOD: rethrow as domain exception
catch (JPAException e) {
    throw new AuditException("Audit write failed", e);
}

// GOOD: outbox compensation for cross-service write
catch (Exception e) {
    writeToOutbox(auditEntry, e);
}

// GOOD: explicitly annotated intentional swallow
catch (Exception e) {
    // ADR-006: @Scheduled must not throw — Spring stops scheduler on uncaught exception.
    // Next scheduled run will retry. Outbox not needed (non-critical side-effect).
    log.error("TTL scan failed", e);
}
```

### Rule 2: GrpcExceptionAdvice must log

All `GrpcExceptionAdvice.translate()` implementations must include structured logging:

```java
public <T> void translate(Throwable t, StreamObserver<T> observer) {
    Status status = toStatus(t);
    if (log.isWarnEnabled()) {
        log.warn("gRPC exception -> status={} desc={}", status.getCode(),
                status.getDescription(), t);
    }
    observer.onError(status.asRuntimeException());
}
```

**Requirements**:
- Log level: `WARN` (business errors are expected; unexpected errors get full stack trace via the `t` argument)
- Include: `status.getCode()`, `status.getDescription()`, and the full exception `t` (for stack trace)
- Use `isWarnEnabled()` guard to avoid string concatenation overhead when WARN is disabled

### Rule 3: gRPC services catch Exception, not Throwable

All gRPC service implementations must use `catch (Exception e)` instead of `catch (Throwable t)`:

```java
// BAD: catches OOM, StackOverflow — should crash the pod
try {
    // ... business logic ...
} catch (Throwable t) {
    advice.translate(t, observer);
}

// GOOD: let Errors (OOM etc.) propagate to crash the pod for K8s restart
try {
    // ... business logic ...
} catch (Exception e) {
    advice.translate(e, observer);
}
```

**Rationale**: `Error` subclasses (OutOfMemoryError, StackOverflowError, InternalError) indicate JVM-level failures that should not be caught. K8s liveness probes will detect the crash and restart the pod. Catching these errors and trying to send a gRPC response is dangerous — the JVM may not have enough memory to even construct the response.

### Rule 4: @Scheduled methods are the only exception

`@Scheduled` methods MUST catch and log exceptions without rethrowing, because Spring's `ScheduledAnnotationBeanPostProcessor` stops the scheduler thread on uncaught exceptions. This is the ONLY acceptable swallow pattern without explicit annotation:

```java
@Scheduled(fixedDelay = 5000)
public void poll() {
    try {
        // ... work ...
    } catch (Exception e) {
        // @Scheduled must not throw — Spring stops scheduler on uncaught exception.
        // Next scheduled run will retry.
        log.error("Scheduled task failed", e);
    }
}
```

Even in this case, the catch must log with full stack trace (`log.error("...", e)` with the exception as the last argument, not just `e.getMessage()`).

### Rule 5: Fallback/degradation patterns must be annotated

When a catch block implements an intentional degradation (e.g., Redis down → in-memory fallback), the intent must be documented:

```java
catch (Exception e) {
    // Intentional degradation: Redis failure falls back to in-memory rate-limiting.
    // ADR-006 compliant: catch + fallback is explicit degradation strategy, not a swallow.
    log.warn("Redis rate-limit failed, degrading to in-memory: ...", e.getMessage());
    return tryAcquireInMemory(tenant, toolId, rate);
}
```

---

## Consequences

### Positive
- All gRPC errors are visible in server logs (12 GrpcExceptionAdvice implementations fixed)
- OOM/StackOverflow errors are no longer swallowed — K8s can auto-restart
- Audit failures propagate correctly through outbox compensation
- Future developers have clear rules to follow (reduces S-12 recurrence)

### Negative
- Slightly more log volume (every gRPC error now gets a WARN log)
- Catch blocks require more thought (no more "just log it")

### Compliance Checklist

For every `catch (Exception/Throwable)` in `src/main/java`:

- [ ] Does it rethrow? → OK
- [ ] Does it write to outbox for compensation? → OK
- [ ] Is it a @Scheduled method? → OK (must log with full stack trace)
- [ ] Is it a documented degradation/fallback? → OK (must have comment)
- [ ] None of the above? → **VIOLATION**: add rethrow, outbox, or annotation

---

## Swallow Point Audit (2026-07-07)

Full scan of `catch\s*\(.*(Exception|Throwable)` across `src/main/java`:

| # | File | Line | Pattern | Disposition |
|---|------|------|---------|-------------|
| 1 | `GrpcExceptionAdvice` (12 files) | translate() | No logging | **FIXED**: added `log.warn` with status + desc + full exception |
| 2 | `ToolCallAuditorImpl.audit()` | catch Exception → log only | **FIXED** (prior session): now rethrows |
| 3 | `ReActLoopImpl.checkpoint` | catch Exception → log only | **FIXED** (prior session): now writes to outbox |
| 4 | `AgentRuntimeGrpcImpl` (5 methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 5 | `MemoryServiceGrpcImpl` (4 methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 6 | `AgentRepoGrpcService` (4 methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 7 | `ToolGatewayGrpcService` (4 methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 8 | `HallucinationGrpcService` (4 methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 9 | `RiskControlGrpcService` (3 methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 10 | `ObservabilityGrpcService` (3 methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 11 | `PlanningGrpcService` (4 methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 12 | `ModelGatewayGrpcService` (6+ methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 13 | `QualityGrpcService` (4 methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 14 | `DriftGrpcService` (4 methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 15 | `KnowledgeBaseGrpcService` (4 methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 16 | `TaskOrchestratorGrpcService` (4 methods) | catch Throwable | **FIXED**: narrowed to catch Exception |
| 17 | `ContentSafetyCheckerImpl.logViolation()` | catch Exception → log.warn(msg only) | **FIXED**: added full stack trace + ADR-006 annotation |
| 18 | `MemoryTtlScheduler.scheduledScan()` | catch Exception → log.error | **OK**: @Scheduled, added annotation |
| 19 | `RateLimiter.tryAcquire()` | catch Exception → in-memory fallback | **OK**: documented degradation, added annotation |
| 20 | `ComplianceAuditorImpl.record()` | catch Exception → rethrow as AuditException | **OK**: rethrows as domain exception |
| 21 | `JsonUtils` (4 methods) | catch Exception\|Error → throw RuntimeException | **OK**: rethrows as unchecked |
| 22 | `HallucinationMapper` | catch Exception → rethrow | **OK**: rethrows |
| 23 | `OutboxRelay.pollPending()` | catch Exception → markFailed | **OK**: state transition, not a swallow |
| 24 | `OutboxConsumer.consume()` | catch Exception → returns false | **OK**: signals retry, not a swallow |
| 25 | `ToolSemanticRecallerImpl` (2 methods) | catch Exception → fallback to keyword | **OK**: documented degradation |
| 26 | `EmbeddingClientImpl.embed()` | catch Exception → rethrow as domain | **OK**: rethrows |
| 27 | `MilvusVectorStoreImpl` | catch Exception → rethrow as domain | **OK**: rethrows |
| 28 | `SessionStreamController` | catch Exception → close with error | **OK**: SSE stream error handling |
| 29 | `ContentSafetyFilter` | catch Exception → deny with error | **OK**: security filter safe-default |
| 30 | `AuthFilter` | catch Exception → deny | **OK**: auth filter safe-default |
| 31 | `DockerSandboxBorrower` (10+ methods) | catch RuntimeException → best-effort cleanup | **OK**: sandbox lifecycle, cleanup must not throw |
| 32 | `SandboxPool` | catch InterruptedException → restore interrupt | **OK**: standard interrupt handling |
| 33 | `MemoryRecordMapper` | catch IllegalArgumentException → rethrow | **OK**: rethrows |
| 34 | `MemoryDeduperImpl` | catch NoSuchAlgorithmException → rethrow as domain | **OK**: rethrows |
| 35 | `LongTermMemoryWriterImpl` | catch NoSuchAlgorithmException → rethrow as domain | **OK**: rethrows |
| 36 | `MemoryDistillerImpl` | catch RuntimeException → rethrow | **OK**: rethrows |
| 37 | `EmbeddingResponseParser` | catch specific → rethrow | **OK**: rethrows |
| 38 | `MemoryServiceClientImpl` | catch StatusRuntimeException → throw domain | **OK**: rethrows |
| 39 | `RecallQueryBuilder` | catch Exception → return empty | **OK**: safe-default for non-critical recall |
| 40 | `ToolCallMapper` | catch IllegalArgumentException → INVALID_ARGUMENT | **OK**: maps to domain error |
| 41 | `HttpExecutor` | catch HttpTimeoutException → TOOL_TIMEOUT | **OK**: maps to domain error |
| 42 | `JsonListConverter` (2 methods) | catch Exception → throw RuntimeException | **OK**: rethrows |
| 43 | `VersionControlImpl` | catch NumberFormatException → throw domain | **OK**: rethrows |
| 44 | `ThinkPhase` | catch RuntimeException → return failure result | **OK**: phase result, not a swallow |
| 45 | `ActPhase` | catch RuntimeException → return failure result | **OK**: phase result, not a swallow |
| 46 | `TaskController` | catch IllegalArgumentException → 400 response | **OK**: HTTP error response |
| 47 | `Slf4jAuditLogService` | catch JsonProcessingException → log + skip | **OK**: best-effort audit logging |
| 48 | `PromptCacheImpl` | catch NoSuchAlgorithmException → rethrow | **OK**: rethrows |
| 49 | `AgentRepositoryImpl` | catch Exception → rethrow as domain | **OK**: rethrows |
| 50 | `PlanningService` | catch Exception → rethrow as domain | **OK**: rethrows |

**Summary**: 100 catch points scanned, 17 violations fixed, 33 compliant as-is.
