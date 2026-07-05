package com.agent.tool.engine.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SandboxPool} 单元测试.
 *
 * <p>Verifies permit acquire/release, spec-matched poll/offer, idle-expiry sweep,
 * and drainAll behavior.</p>
 */
class SandboxPoolTest {

    private static SandboxSpec spec(String image) {
        return SandboxSpec.builder().image(image).build();
    }

    private static SandboxInstance instance(String id, SandboxSpec spec) {
        return new SandboxInstance(id, spec);
    }

    @Test
    @DisplayName("acquirePermit: maxConcurrent=2, 第三次 acquire 超时返回 false")
    void acquirePermit_returnsFalse_When_SemaphoreExhausted() {
        SandboxPool pool = new SandboxPool(5, 2);
        assertThat(pool.acquirePermit(100)).isTrue();
        assertThat(pool.acquirePermit(100)).isTrue();
        assertThat(pool.acquirePermit(100)).isFalse(); // exhausted
        assertThat(pool.activeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("releasePermit: 释放后 activeCount 减少, 可再次 acquire")
    void releasePermit_decrementsActiveCount() {
        SandboxPool pool = new SandboxPool(5, 1);
        assertThat(pool.acquirePermit(100)).isTrue();
        assertThat(pool.activeCount()).isEqualTo(1);

        pool.releasePermit();

        assertThat(pool.activeCount()).isZero();
        assertThat(pool.acquirePermit(100)).isTrue();
    }

    @Test
    @DisplayName("acquirePermit: 中断线程 → 返回 false + 重置中断标志")
    void acquirePermit_returnsFalse_When_Interrupted() throws InterruptedException {
        SandboxPool pool = new SandboxPool(5, 1);
        assertThat(pool.acquirePermit(100)).isTrue();

        Thread t = Thread.currentThread();
        Thread interrupter = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            t.interrupt();
        });
        interrupter.start();

        boolean acquired = pool.acquirePermit(5000); // blocks, will be interrupted
        assertThat(acquired).isFalse();
        assertThat(Thread.interrupted()).isTrue(); // flag should be set
        interrupter.join(1000);
    }

    @Test
    @DisplayName("poll: spec=null → 返回任意一个空闲实例")
    void poll_returnsAny_When_SpecNull() {
        SandboxPool pool = new SandboxPool(5, 5);
        SandboxInstance a = instance("c-a", spec("img-a"));
        SandboxInstance b = instance("c-b", spec("img-b"));
        pool.offer(a);
        pool.offer(b);

        Optional<SandboxInstance> polled = pool.poll(null);

        assertThat(polled).isPresent();
        assertThat(pool.idleSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("poll: spec 匹配 → 返回匹配实例, 不匹配的放回池")
    void poll_returnsMatchingSpec_When_PoolHasMatch() {
        SandboxPool pool = new SandboxPool(5, 5);
        SandboxSpec target = spec("img-target");
        pool.offer(instance("c-a", spec("img-a")));
        pool.offer(instance("c-b", target));
        pool.offer(instance("c-c", spec("img-c")));

        Optional<SandboxInstance> polled = pool.poll(target);

        assertThat(polled).isPresent();
        assertThat(polled.get().getContainerId()).isEqualTo("c-b");
        assertThat(pool.idleSize()).isEqualTo(2); // others kept
    }

    @Test
    @DisplayName("poll: 池空或无匹配 → 返回 empty")
    void poll_returnsEmpty_When_NoMatch() {
        SandboxPool pool = new SandboxPool(5, 5);
        assertThat(pool.poll(spec("img-x"))).isEmpty();

        pool.offer(instance("c-a", spec("img-a")));
        assertThat(pool.poll(spec("img-x"))).isEmpty();
        assertThat(pool.idleSize()).isEqualTo(1); // un-matched kept
    }

    @Test
    @DisplayName("offer: 池满 (idleSize >= poolSize) → 返回 false, 不入池")
    void offer_returnsFalse_When_PoolFull() {
        SandboxPool pool = new SandboxPool(2, 5);
        assertThat(pool.offer(instance("c-1", spec("img")))).isTrue();
        assertThat(pool.offer(instance("c-2", spec("img")))).isTrue();
        assertThat(pool.offer(instance("c-3", spec("img")))).isFalse(); // full
        assertThat(pool.idleSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("offer: null 实例 → 返回 false")
    void offer_returnsFalse_When_InstanceNull() {
        SandboxPool pool = new SandboxPool(2, 5);
        assertThat(pool.offer(null)).isFalse();
        assertThat(pool.idleSize()).isZero();
    }

    @Test
    @DisplayName("offer: 入池时更新 lastUsedAt (touch)")
    void offer_touchesInstance_UpdatingLastUsedAt() {
        SandboxPool pool = new SandboxPool(5, 5);
        SandboxInstance old = new SandboxInstance("c-old", spec("img"),
                Instant.now().minus(10, ChronoUnit.MINUTES),
                Instant.now().minus(10, ChronoUnit.MINUTES));
        Instant before = old.getLastUsedAt();

        pool.offer(old);

        assertThat(old.getLastUsedAt()).isAfter(before);
    }

    @Test
    @DisplayName("sweepExpired: 返回 lastUsedAt 早于 cutoff 的实例, 其余保留")
    void sweepExpired_returnsExpiredInstances() {
        SandboxPool pool = new SandboxPool(5, 5);
        SandboxInstance expired = new SandboxInstance("c-exp", spec("img"),
                Instant.now().minus(10, ChronoUnit.MINUTES),
                Instant.now().minus(10, ChronoUnit.MINUTES));
        SandboxInstance fresh = new SandboxInstance("c-fresh", spec("img"));
        // offerRaw skips touch() so expired keeps its old lastUsedAt.
        pool.offerRaw(expired);
        pool.offer(fresh); // fresh gets touched → lastUsedAt = now

        List<SandboxInstance> expiredList = pool.sweepExpired(60_000L); // 1 min cutoff

        assertThat(expiredList).hasSize(1);
        assertThat(expiredList.get(0).getContainerId()).isEqualTo("c-exp");
        assertThat(pool.idleSize()).isEqualTo(1); // fresh kept
    }

    @Test
    @DisplayName("sweepExpired: 无过期 → 返回空列表")
    void sweepExpired_returnsEmpty_When_NoneExpired() {
        SandboxPool pool = new SandboxPool(5, 5);
        pool.offer(instance("c-fresh", spec("img")));

        List<SandboxInstance> expired = pool.sweepExpired(60_000L);

        assertThat(expired).isEmpty();
        assertThat(pool.idleSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("drainAll: 清空池, 返回所有实例")
    void drainAll_removesAndReturnsAllInstances() {
        SandboxPool pool = new SandboxPool(5, 5);
        pool.offer(instance("c-1", spec("img")));
        pool.offer(instance("c-2", spec("img")));

        List<SandboxInstance> drained = pool.drainAll();

        assertThat(drained).hasSize(2);
        assertThat(pool.idleSize()).isZero();
    }

    @Test
    @DisplayName("idleSize: 反映当前空闲池大小")
    void idleSize_reflectsCurrentPoolSize() {
        SandboxPool pool = new SandboxPool(5, 5);
        assertThat(pool.idleSize()).isZero();

        pool.offer(instance("c-1", spec("img")));
        assertThat(pool.idleSize()).isEqualTo(1);

        pool.poll(spec("img"));
        assertThat(pool.idleSize()).isZero();
    }
}
