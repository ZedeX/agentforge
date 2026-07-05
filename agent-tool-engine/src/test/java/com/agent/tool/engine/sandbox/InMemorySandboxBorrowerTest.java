package com.agent.tool.engine.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InMemorySandboxBorrower} 单元测试.
 *
 * <p>Covers both the T6 spec-based API ({@code borrow(Spec)} / {@code exec} /
 * {@code release} / {@code cleanupExpired}) and the legacy API
 * ({@code borrow()} / {@code recycle()}).</p>
 */
class InMemorySandboxBorrowerTest {

    private final InMemorySandboxBorrower borrower = new InMemorySandboxBorrower();

    // ============ T6 spec-based API ============

    @Test
    @DisplayName("borrow(Spec): 返回非空 containerId, spec 透传")
    void borrow_returnsInstance_When_CalledWithSpec() {
        SandboxSpec spec = SandboxSpec.builder().image("custom:latest").build();

        SandboxInstance instance = borrower.borrow(spec);

        assertThat(instance).isNotNull();
        assertThat(instance.getContainerId()).startsWith("sb-");
        assertThat(instance.getSpec()).isEqualTo(spec);
        assertThat(borrower.activeCount()).isEqualTo(1);
        assertThat(borrower.isActive(instance.getContainerId())).isTrue();
    }

    @Test
    @DisplayName("borrow 多次: 返回递增唯一 containerId")
    void borrow_returnsIncrementalIds_When_CalledMultipleTimes() {
        SandboxInstance i1 = borrower.borrow(SandboxSpec.builder().build());
        SandboxInstance i2 = borrower.borrow(SandboxSpec.builder().build());
        SandboxInstance i3 = borrower.borrow(SandboxSpec.builder().build());

        assertThat(i1.getContainerId()).isNotEqualTo(i2.getContainerId());
        assertThat(i2.getContainerId()).isNotEqualTo(i3.getContainerId());
        assertThat(borrower.activeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("exec: 返回罐头 stdout (echo command + containerId)")
    void exec_returnsCannedStdout_When_CalledOnActiveContainer() {
        SandboxInstance instance = borrower.borrow(SandboxSpec.builder().build());

        SandboxExecResult result = borrower.exec(instance.getContainerId(),
                List.of("echo", "hello"), Map.of(), 1000L);

        assertThat(result.getStdout()).contains(instance.getContainerId());
        assertThat(result.getStdout()).contains("echo hello");
        assertThat(result.getExitCode()).isZero();
        assertThat(result.isTimedOut()).isFalse();
        assertThat(result.isSuccessful()).isTrue();
    }

    @Test
    @DisplayName("exec: containerId 不在 active 中 → exitCode=127 错误")
    void exec_returnsError_When_ContainerIdUnknown() {
        SandboxExecResult result = borrower.exec("sb-ghost",
                List.of("echo"), Map.of(), 1000L);

        assertThat(result.getExitCode()).isEqualTo(127);
        assertThat(result.getStderr()).contains("sb-ghost");
        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    @DisplayName("release: 已借用容器 → 从 active 移除, activeCount 减少")
    void release_removesFromActive_When_CalledOnActiveContainer() {
        SandboxInstance i1 = borrower.borrow(SandboxSpec.builder().build());
        SandboxInstance i2 = borrower.borrow(SandboxSpec.builder().build());
        assertThat(borrower.activeCount()).isEqualTo(2);

        borrower.release(i1.getContainerId());

        assertThat(borrower.activeCount()).isEqualTo(1);
        assertThat(borrower.isActive(i1.getContainerId())).isFalse();
        assertThat(borrower.isActive(i2.getContainerId())).isTrue();
    }

    @Test
    @DisplayName("release: 未知 containerId → 安全跳过, activeCount 不变")
    void release_isSafe_When_ContainerIdUnknown() {
        borrower.borrow(SandboxSpec.builder().build());
        assertThat(borrower.activeCount()).isEqualTo(1);

        borrower.release("sb-unknown");

        assertThat(borrower.activeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("release: null 或空白 containerId → 安全跳过")
    void release_isSafe_When_ContainerIdNullOrBlank() {
        borrower.borrow(SandboxSpec.builder().build());
        assertThat(borrower.activeCount()).isEqualTo(1);

        borrower.release(null);
        borrower.release("  ");

        assertThat(borrower.activeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("cleanupExpired: 内存模式无 idle 池 → 始终返回 0")
    void cleanupExpired_returnsZero_When_InMemoryMode() {
        borrower.borrow(SandboxSpec.builder().build());
        assertThat(borrower.cleanupExpired()).isZero();
    }

    // ============ Legacy API ============

    @Test
    @DisplayName("legacy borrow(): 返回默认 spec 的 containerId 字符串")
    void legacyBorrow_returnsContainerIdString_With_DefaultSpec() {
        String id = borrower.borrow();
        assertThat(id).startsWith("sb-");
        assertThat(borrower.activeCount()).isEqualTo(1);
        assertThat(borrower.isActive(id)).isTrue();
    }

    @Test
    @DisplayName("legacy recycle(): 等价于 release(containerId)")
    void legacyRecycle_equivalentToRelease() {
        String id1 = borrower.borrow();
        String id2 = borrower.borrow();
        assertThat(borrower.activeCount()).isEqualTo(2);

        borrower.recycle(id1);

        assertThat(borrower.activeCount()).isEqualTo(1);
        assertThat(borrower.isActive(id1)).isFalse();
        assertThat(borrower.isActive(id2)).isTrue();
    }

    @Test
    @DisplayName("legacy recycle(): 未知 id → 安全跳过")
    void legacyRecycle_isSafe_When_UnknownId() {
        borrower.borrow();
        assertThat(borrower.activeCount()).isEqualTo(1);

        borrower.recycle("sb-ghost");

        assertThat(borrower.activeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("legacy recycle(): null 或空白 → 安全跳过")
    void legacyRecycle_isSafe_When_NullOrBlank() {
        borrower.borrow();
        assertThat(borrower.activeCount()).isEqualTo(1);

        borrower.recycle(null);
        borrower.recycle("  ");

        assertThat(borrower.activeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("borrow + release 完整循环: activeCount 归零")
    void borrowReleaseCycle_returnsToZero() {
        String id = borrower.borrow();
        assertThat(borrower.activeCount()).isEqualTo(1);

        borrower.recycle(id);

        assertThat(borrower.activeCount()).isZero();
        assertThat(borrower.isActive(id)).isFalse();
    }
}
