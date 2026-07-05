package com.agent.tool.engine.sandbox;

import com.agent.tool.engine.config.ToolEngineProperties;
import com.agent.tool.engine.exception.ToolSandboxFailureException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.KillContainerCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DockerSandboxBorrower} 单元测试.
 *
 * <p>Mock {@link DockerClient} + 全量模拟 docker-java 命令链
 * (createContainerCmd / startContainerCmd / execCreateCmd / execStartCmd /
 * inspectExecCmd / killContainerCmd / removeContainerCmd).</p>
 *
 * <p>{@link DockerSandboxBorrower} is excluded from JaCoCo coverage
 * (pattern {@code Docker*} in sandbox package), so this test verifies
 * behavior only — coverage does not count.</p>
 */
class DockerSandboxBorrowerTest {

    private DockerClient dockerClient;
    private ToolEngineProperties properties;
    private DockerSandboxBorrower borrower;

    @BeforeEach
    void setUp() {
        dockerClient = mock(DockerClient.class);
        properties = new ToolEngineProperties();
        // Test-friendly: small pool / maxConcurrent / borrow timeout for fast tests.
        properties.getSandbox().setPoolSize(2);
        properties.getSandbox().setMaxConcurrent(2);
        properties.getSandbox().setBorrowTimeoutMs(500L);
        properties.getSandbox().setIdleTimeoutMs(1000L);
        borrower = new DockerSandboxBorrower(dockerClient, properties);
    }

    // ============ borrow ============

    @Test
    @DisplayName("borrow: 池空冷启动 → 创建新容器, 返回非空 containerId")
    void borrow_returnsSandboxInstance_When_ColdStart() {
        stubCreateContainer("agent-sandbox:latest", "c-cold-1");

        SandboxInstance instance = borrower.borrow(SandboxSpec.builder().build());

        assertThat(instance).isNotNull();
        assertThat(instance.getContainerId()).isEqualTo("c-cold-1");
        assertThat(borrower.activeCount()).isEqualTo(1);
        verify(dockerClient).createContainerCmd("agent-sandbox:latest");
        verify(dockerClient).startContainerCmd("c-cold-1");
    }

    @Test
    @DisplayName("borrow: 池中有匹配 spec 的空闲容器 → 复用, 不创建新容器")
    void borrow_reusesContainer_When_WarmPoolHasMatch() {
        SandboxSpec spec = SandboxSpec.builder().build();
        SandboxInstance warm = new SandboxInstance("c-warm-1", spec);
        borrower.offerToPool(warm);

        SandboxInstance instance = borrower.borrow(spec);

        assertThat(instance.getContainerId()).isEqualTo("c-warm-1");
        assertThat(borrower.idlePoolSize()).isZero();
        verify(dockerClient, never()).createContainerCmd(any());
    }

    @Test
    @DisplayName("borrow: 池有容器但 spec 不匹配 → 创建新容器")
    void borrow_createsNew_When_WarmPoolSpecMismatch() {
        SandboxSpec warmSpec = SandboxSpec.builder().image("old-image:latest").build();
        borrower.offerToPool(new SandboxInstance("c-warm-old", warmSpec));
        SandboxSpec requestedSpec = SandboxSpec.builder().image("agent-sandbox:latest").build();
        stubCreateContainer("agent-sandbox:latest", "c-new-1");

        SandboxInstance instance = borrower.borrow(requestedSpec);

        assertThat(instance.getContainerId()).isEqualTo("c-new-1");
        verify(dockerClient).createContainerCmd("agent-sandbox:latest");
    }

    @Test
    @DisplayName("borrow: 池满 (maxConcurrent) → 阻塞等待超时, 抛 ToolSandboxFailureException (POOL_EXHAUSTED)")
    void borrow_throwsPoolExhausted_When_SemaphoreFull() {
        // Exhaust both permits with two active borrows (no release).
        stubCreateContainer("agent-sandbox:latest", "c-exhaust-1");
        stubCreateContainer("agent-sandbox:latest", "c-exhaust-2");
        borrower.borrow(SandboxSpec.builder().build());
        borrower.borrow(SandboxSpec.builder().build());
        assertThat(borrower.activeCount()).isEqualTo(2);

        // Third borrow should time out (borrowTimeoutMs=500ms).
        assertThatThrownBy(() -> borrower.borrow(SandboxSpec.builder().build()))
                .isInstanceOf(ToolSandboxFailureException.class)
                .satisfies(ex -> assertThat(((ToolSandboxFailureException) ex).getErrorCode())
                        .isEqualTo(ToolSandboxFailureException.CODE_POOL_EXHAUSTED));
    }

    @Test
    @DisplayName("borrow: createContainer 抛异常 → 包装为 ToolSandboxFailureException")
    void borrow_wrapsDockerFailure_When_CreateContainerThrows() {
        when(dockerClient.createContainerCmd(any())).thenThrow(new RuntimeException("docker daemon down"));

        assertThatThrownBy(() -> borrower.borrow(SandboxSpec.builder().build()))
                .isInstanceOf(ToolSandboxFailureException.class)
                .hasMessageContaining("create container failed");
    }

    // ============ exec ============

    @Test
    @DisplayName("exec: 命令成功 → 返回 stdout / stderr / exitCode=0")
    void exec_returnsStdout_When_CommandSucceeds() {
        stubExec("c-exec-ok", "exec-1", "hello\n", "err-warn\n", 0, true);

        SandboxExecResult result = borrower.exec("c-exec-ok", List.of("sh", "-c", "echo hello"),
                Map.of("FOO", "bar"), 5000L);

        assertThat(result.getStdout()).isEqualTo("hello\n");
        assertThat(result.getStderr()).isEqualTo("err-warn\n");
        assertThat(result.getExitCode()).isZero();
        assertThat(result.isTimedOut()).isFalse();
        assertThat(result.isSuccessful()).isTrue();
    }

    @Test
    @DisplayName("exec: 命令返回非零 exitCode → exitCode 透传, isSuccessful=false")
    void exec_returnsExitCode_When_CommandFails() {
        stubExec("c-exec-fail", "exec-2", "", "command not found\n", 127, true);

        SandboxExecResult result = borrower.exec("c-exec-fail", List.of("badcmd"),
                Map.of(), 5000L);

        assertThat(result.getExitCode()).isEqualTo(127);
        assertThat(result.isTimedOut()).isFalse();
        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    @DisplayName("exec: 超时 → 杀容器 + 标记 timedOut=true, exitCode=-1")
    void exec_killsContainer_When_TimedOut() {
        String containerId = "c-exec-timeout";
        String execId = "exec-timeout-1";
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execResp = mock(ExecCreateCmdResponse.class);
        when(execResp.getId()).thenReturn(execId);
        when(execCreateCmd.withAttachStdout(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withEnv(any(List.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execResp);
        when(dockerClient.execCreateCmd(containerId)).thenReturn(execCreateCmd);

        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        // Don't trigger callback completion → awaitCompletion will time out.
        when(execStartCmd.exec(any())).thenAnswer(inv -> {
            ExecStartResultCallback cb = inv.getArgument(0);
            // Start but never complete — simulates a hung command.
            cb.onStart(() -> {});
            return cb;
        });
        when(dockerClient.execStartCmd(execId)).thenReturn(execStartCmd);

        KillContainerCmd killCmd = mock(KillContainerCmd.class);
        when(dockerClient.killContainerCmd(containerId)).thenReturn(killCmd);

        SandboxExecResult result = borrower.exec(containerId, List.of("sleep", "999"),
                Map.of(), 200L);

        assertThat(result.isTimedOut()).isTrue();
        assertThat(result.getExitCode()).isEqualTo(-1);
        verify(killCmd, times(1)).exec();
    }

    @Test
    @DisplayName("exec: containerId 为空 → 返回 exitCode=127 错误结果")
    void exec_returnsError_When_ContainerIdBlank() {
        SandboxExecResult result = borrower.exec("", List.of("echo"), Map.of(), 1000L);

        assertThat(result.getExitCode()).isEqualTo(127);
        assertThat(result.getStderr()).contains("null/blank");
        assertThat(result.isSuccessful()).isFalse();
    }

    // ============ release ============

    @Test
    @DisplayName("release: 空闲池未满 → 容器还池 (复用)")
    void release_returnsToPool_When_PoolHasRoom() {
        stubCreateContainer("agent-sandbox:latest", "c-rel-1");
        SandboxInstance instance = borrower.borrow(SandboxSpec.builder().build());
        assertThat(borrower.idlePoolSize()).isZero();

        borrower.release(instance.getContainerId());

        assertThat(borrower.idlePoolSize()).isEqualTo(1);
        assertThat(borrower.activeCount()).isZero();
        verify(dockerClient, never()).removeContainerCmd(any());
    }

    @Test
    @DisplayName("release: 空闲池已满 → 强制销毁容器 (removeContainerCmd force=true)")
    void release_destroysContainer_When_PoolFull() {
        // Cold-start borrow (pool empty → creates "c-rel-2").
        stubCreateContainer("agent-sandbox:latest", "c-rel-2");
        SandboxInstance instance = borrower.borrow(SandboxSpec.builder().build());
        assertThat(borrower.idlePoolSize()).isZero();

        // Now fill the pool to capacity (poolSize=2) so release can't return.
        borrower.offerToPool(new SandboxInstance("c-pool-1", SandboxSpec.builder().build()));
        borrower.offerToPool(new SandboxInstance("c-pool-2", SandboxSpec.builder().build()));
        assertThat(borrower.idlePoolSize()).isEqualTo(2);

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);
        when(removeCmd.withRemoveVolumes(true)).thenReturn(removeCmd);
        when(dockerClient.removeContainerCmd("c-rel-2")).thenReturn(removeCmd);

        borrower.release(instance.getContainerId());

        // Pool was full → container destroyed, not returned to pool.
        assertThat(borrower.idlePoolSize()).isEqualTo(2);
        verify(removeCmd, times(1)).exec();
    }

    @Test
    @DisplayName("release: 未知 containerId → 仍尝试销毁 + 释放 permit")
    void release_destroysOrphan_When_ContainerIdUnknown() {
        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);
        when(removeCmd.withRemoveVolumes(true)).thenReturn(removeCmd);
        when(dockerClient.removeContainerCmd("c-orphan")).thenReturn(removeCmd);

        // Borrow a real one so a permit is held, then release an unknown id.
        // (Releasing unknown id should still release the permit it would have held.)
        borrower.release("c-orphan");

        verify(removeCmd, times(1)).exec();
    }

    // ============ cleanupExpired ============

    @Test
    @DisplayName("cleanupExpired: 扫描 idle 池, 销毁 lastUsedAt 早于 idleTimeoutMs 的容器")
    void cleanupExpired_destroysIdleExpiredContainers() {
        // Insert an expired instance (offerRaw skips touch so lastUsedAt stays old).
        SandboxSpec spec = SandboxSpec.builder().build();
        SandboxInstance expired = new SandboxInstance("c-expired", spec,
                Instant.now().minus(2, ChronoUnit.MINUTES),
                Instant.now().minus(2, ChronoUnit.MINUTES));
        borrower.offerToPoolRaw(expired);
        // Insert a fresh instance (offer touches → lastUsedAt = now).
        SandboxInstance fresh = new SandboxInstance("c-fresh", spec);
        borrower.offerToPool(fresh);
        assertThat(borrower.idlePoolSize()).isEqualTo(2);

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);
        when(removeCmd.withRemoveVolumes(true)).thenReturn(removeCmd);
        when(dockerClient.removeContainerCmd("c-expired")).thenReturn(removeCmd);

        int destroyed = borrower.cleanupExpired();

        assertThat(destroyed).isEqualTo(1);
        assertThat(borrower.idlePoolSize()).isEqualTo(1);
        verify(removeCmd, times(1)).exec();
    }

    @Test
    @DisplayName("cleanupExpired: 无过期容器 → 返回 0, 不调用 removeContainerCmd")
    void cleanupExpired_returnsZero_When_NoExpired() {
        SandboxInstance fresh = new SandboxInstance("c-fresh", SandboxSpec.builder().build());
        borrower.offerToPool(fresh);

        int destroyed = borrower.cleanupExpired();

        assertThat(destroyed).isZero();
        verify(dockerClient, never()).removeContainerCmd(any());
    }

    // ============ Legacy API ============

    @Test
    @DisplayName("legacy borrow() + recycle(): 使用默认 spec 借用并回收")
    void legacyBorrowAndRecycle_Work_With_DefaultSpec() {
        stubCreateContainer("agent-sandbox:latest", "c-legacy-1");

        String containerId = borrower.borrow();
        assertThat(containerId).isEqualTo("c-legacy-1");
        assertThat(borrower.activeCount()).isEqualTo(1);

        borrower.recycle(containerId);
        assertThat(borrower.activeCount()).isZero();
        assertThat(borrower.idlePoolSize()).isEqualTo(1); // returned to pool
    }

    // ============ Helpers ============

    /** Stub the createContainerCmd → startContainerCmd chain to return a fake containerId. */
    private void stubCreateContainer(String image, String containerId) {
        CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse createResp = mock(CreateContainerResponse.class);
        when(createResp.getId()).thenReturn(containerId);
        when(createCmd.withHostConfig(any())).thenReturn(createCmd);
        when(createCmd.withCmd(any(String[].class))).thenReturn(createCmd);
        when(createCmd.withEnv(any(List.class))).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(createResp);
        when(dockerClient.createContainerCmd(image)).thenReturn(createCmd);

        StartContainerCmd startCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(containerId)).thenReturn(startCmd);
    }

    /** Stub the full exec chain: execCreateCmd → execStartCmd (writes frame) → inspectExecCmd. */
    private void stubExec(String containerId, String execId, String stdout, String stderr,
                          int exitCode, boolean complete) {
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execResp = mock(ExecCreateCmdResponse.class);
        when(execResp.getId()).thenReturn(execId);
        when(execCreateCmd.withAttachStdout(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withEnv(any(List.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execResp);
        when(dockerClient.execCreateCmd(containerId)).thenReturn(execCreateCmd);

        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        when(execStartCmd.exec(any())).thenAnswer(inv -> {
            ExecStartResultCallback cb = inv.getArgument(0);
            cb.onStart(() -> {});
            if (stdout != null && !stdout.isEmpty()) {
                cb.onNext(new Frame(StreamType.STDOUT, stdout.getBytes(StandardCharsets.UTF_8)));
            }
            if (stderr != null && !stderr.isEmpty()) {
                cb.onNext(new Frame(StreamType.STDERR, stderr.getBytes(StandardCharsets.UTF_8)));
            }
            if (complete) {
                cb.onComplete();
            }
            return cb;
        });
        when(dockerClient.execStartCmd(execId)).thenReturn(execStartCmd);

        InspectExecCmd inspectCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectResp = mock(InspectExecResponse.class);
        when(inspectResp.getExitCode()).thenReturn(exitCode);
        when(inspectCmd.exec()).thenReturn(inspectResp);
        when(dockerClient.inspectExecCmd(execId)).thenReturn(inspectCmd);
    }
}
