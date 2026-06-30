package com.agent.repo.api.impl;

import com.agent.repo.enums.AgentStatus;
import com.agent.repo.enums.AgentTier;
import com.agent.repo.model.AgentDefinition;
import com.agent.repo.model.AgentVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * VersionControlImpl unit tests (doc 06-agent-repo §4.2).
 */
@DisplayName("VersionControlImpl 版本快照管理器")
class VersionControlImplTest {

    private final VersionControlImpl vc = new VersionControlImpl();

    private AgentDefinition sample(String id, int version) {
        AgentDefinition a = new AgentDefinition(id, "Agent " + id);
        a.setAgentTier(AgentTier.STANDARD);
        a.setStatus(AgentStatus.DRAFT);
        a.setVersion(version);
        a.setDescription("desc-" + version);
        a.setSystemPrompt("prompt-" + version);
        a.setMaxSteps(10);
        a.setMaxToken(4096);
        return a;
    }

    @Test
    @DisplayName("snapshot 后能按 agentId 列出该版本")
    void should_ListVersion_When_Snapshotted() {
        vc.snapshot(sample("agent-1", 1), "initial");
        List<AgentVersion> versions = vc.listVersions("agent-1");
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getVersion()).isEqualTo(1);
        assertThat(versions.get(0).getChangeLog()).isEqualTo("initial");
        assertThat(versions.get(0).getSnapshot()).contains("agent-1");
        assertThat(versions.get(0).getCreatedAt()).isPositive();
    }

    @Test
    @DisplayName("listVersions 按版本号降序返回")
    void should_ReturnDescendingByVersion_When_ListVersionsCalled() {
        vc.snapshot(sample("agent-1", 1), "v1");
        vc.snapshot(sample("agent-1", 2), "v2");
        vc.snapshot(sample("agent-1", 3), "v3");
        List<AgentVersion> versions = vc.listVersions("agent-1");
        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).getVersion()).isEqualTo(3);
        assertThat(versions.get(2).getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("getVersion 精确返回指定版本")
    void should_ReturnSpecificVersion_When_GetVersionCalled() {
        vc.snapshot(sample("agent-1", 1), "v1");
        vc.snapshot(sample("agent-1", 2), "v2");
        Optional<AgentVersion> v2 = vc.getVersion("agent-1", 2);
        assertThat(v2).isPresent();
        assertThat(v2.get().getChangeLog()).isEqualTo("v2");
        assertThat(vc.getVersion("agent-1", 99)).isEmpty();
    }

    @Test
    @DisplayName("rollback 用 snapshot 恢复关键字段")
    void should_RestoreFields_When_RollbackCalled() {
        AgentDefinition v1 = sample("agent-1", 1);
        v1.setDescription("original-desc");
        v1.setSystemPrompt("original-prompt");
        vc.snapshot(v1, "v1");

        // Now simulate update — current agent has different fields
        AgentDefinition current = sample("agent-1", 2);
        current.setDescription("modified-desc");
        current.setSystemPrompt("modified-prompt");

        Optional<AgentDefinition> restored = vc.rollback(current, 1);
        assertThat(restored).isPresent();
        assertThat(restored.get().getDescription()).isEqualTo("original-desc");
        assertThat(restored.get().getSystemPrompt()).isEqualTo("original-prompt");
        // version from snapshot is restored
        assertThat(restored.get().getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("rollback 不存在的版本返回 empty")
    void should_ReturnEmpty_When_RollbackToMissingVersion() {
        vc.snapshot(sample("agent-1", 1), "v1");
        AgentDefinition current = sample("agent-1", 2);
        assertThat(vc.rollback(current, 99)).isEmpty();
    }

    @Test
    @DisplayName("rollback 不存在的 agentId 返回 empty")
    void should_ReturnEmpty_When_AgentHasNoSnapshots() {
        AgentDefinition current = sample("agent-1", 1);
        assertThat(vc.rollback(current, 1)).isEmpty();
    }

    @Test
    @DisplayName("countVersions 反映当前快照数")
    void should_ReturnCount_When_CountVersionsCalled() {
        assertThat(vc.countVersions("agent-1")).isZero();
        vc.snapshot(sample("agent-1", 1), "v1");
        vc.snapshot(sample("agent-1", 2), "v2");
        assertThat(vc.countVersions("agent-1")).isEqualTo(2);
    }

    @Test
    @DisplayName("同 version 第二次 snapshot 幂等, 返回已存在的快照")
    void should_BeIdempotent_When_SameVersionSnapshotTwice() {
        AgentDefinition a = sample("agent-1", 1);
        AgentVersion v1First = vc.snapshot(a, "v1");
        AgentVersion v1Second = vc.snapshot(a, "v1-other");
        assertThat(v1Second.getId()).isEqualTo(v1First.getId());
        assertThat(v1Second.getChangeLog()).isEqualTo("v1"); // original changeLog preserved
        assertThat(vc.countVersions("agent-1")).isEqualTo(1);
    }

    @Test
    @DisplayName("maxHistory=3 时第 4 个 snapshot FIFO 淘汰最旧")
    void should_EvictOldest_When_ExceedMaxHistory() {
        VersionControlImpl small = new VersionControlImpl(3);
        small.snapshot(sample("agent-1", 1), "v1");
        small.snapshot(sample("agent-1", 2), "v2");
        small.snapshot(sample("agent-1", 3), "v3");
        assertThat(small.countVersions("agent-1")).isEqualTo(3);
        // Add v4 → v1 (oldest) should be evicted
        small.snapshot(sample("agent-1", 4), "v4");
        assertThat(small.countVersions("agent-1")).isEqualTo(3);
        assertThat(small.getVersion("agent-1", 1)).isEmpty();
        assertThat(small.getVersion("agent-1", 4)).isPresent();
    }

    @Test
    @DisplayName("snapshot null agent / null agentId 抛 IllegalArgumentException")
    void should_Throw_When_SnapshotNullOrNullId() {
        assertThatThrownBy(() -> vc.snapshot(null, "log"))
                .isInstanceOf(IllegalArgumentException.class);
        AgentDefinition nullId = new AgentDefinition();
        assertThatThrownBy(() -> vc.snapshot(nullId, "log"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("snapshot version <= 0 抛 IllegalArgumentException")
    void should_Throw_When_VersionNonPositive() {
        AgentDefinition a = sample("agent-1", 0);
        assertThatThrownBy(() -> vc.snapshot(a, "log"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("snapshot null changeLog 兜底为空字符串")
    void should_DefaultToEmpty_When_ChangeLogNull() {
        AgentVersion v = vc.snapshot(sample("agent-1", 1), null);
        assertThat(v.getChangeLog()).isEmpty();
    }

    @Test
    @DisplayName("不同 agentId 的快照互不影响")
    void should_IsolateByAgentId_When_DifferentAgents() {
        vc.snapshot(sample("agent-1", 1), "a1-v1");
        vc.snapshot(sample("agent-2", 1), "a2-v1");
        assertThat(vc.countVersions("agent-1")).isEqualTo(1);
        assertThat(vc.countVersions("agent-2")).isEqualTo(1);
        assertThat(vc.listVersions("agent-1").get(0).getAgentId()).isEqualTo("agent-1");
    }

    @Test
    @DisplayName("listVersions / getVersion / countVersions 对 null agentId 安全返回")
    void should_ReturnEmpty_When_NullAgentIdPassed() {
        assertThat(vc.listVersions(null)).isEmpty();
        assertThat(vc.getVersion(null, 1)).isEmpty();
        assertThat(vc.countVersions(null)).isZero();
    }

    @Test
    @DisplayName("rollback 保留 current agent 的 boundTools / boundKnowledgeIds")
    void should_PreserveBindings_When_Rollback() {
        AgentDefinition v1 = sample("agent-1", 1);
        vc.snapshot(v1, "v1");
        AgentDefinition current = sample("agent-1", 2);
        current.setBoundTools(java.util.List.of("tool-a"));
        current.setBoundKnowledgeIds(java.util.List.of("kb-1"));
        Optional<AgentDefinition> restored = vc.rollback(current, 1);
        assertThat(restored).isPresent();
        assertThat(restored.get().getBoundTools()).containsExactly("tool-a");
        assertThat(restored.get().getBoundKnowledgeIds()).containsExactly("kb-1");
    }
}
