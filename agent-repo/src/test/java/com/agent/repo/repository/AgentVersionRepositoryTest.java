package com.agent.repo.repository;

import com.agent.repo.model.AgentVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentVersionRepository JPA integration tests (Plan 08 T3).
 *
 * <p>Verifies version history ordering, latest-version retrieval, and (agentId, version)
 * unique constraint enforcement.</p>
 */
@DisplayName("AgentVersionRepository JPA 仓储测试")
@DataJpaTest
@ActiveProfiles("test")
class AgentVersionRepositoryTest {

    @Autowired
    private AgentVersionRepository repository;

    private AgentVersion buildVersion(String agentId, int version, String changeLog) {
        AgentVersion v = new AgentVersion();
        v.setAgentId(agentId);
        v.setVersion(version);
        v.setSnapshot("{\"agentId\":\"" + agentId + "\",\"version\":" + version + "}");
        v.setChangeLog(changeLog);
        return v;
    }

    @Test
    @DisplayName("findByAgentIdOrderByVersionDesc 按版本号降序返回历史")
    void should_ReturnVersionDesc_When_FindByAgentId() {
        repository.save(buildVersion("agent-v1", 1, "initial"));
        repository.save(buildVersion("agent-v1", 3, "third release"));
        repository.save(buildVersion("agent-v1", 2, "second release"));

        List<AgentVersion> history = repository.findByAgentIdOrderByVersionDesc("agent-v1");

        assertThat(history).hasSize(3);
        assertThat(history).extracting(AgentVersion::getVersion).containsExactly(3, 2, 1);
    }

    @Test
    @DisplayName("findTopByAgentIdOrderByVersionDesc 返回最新版本")
    void should_ReturnLatestVersion_When_FindTop() {
        repository.save(buildVersion("agent-top", 1, "v1"));
        repository.save(buildVersion("agent-top", 5, "v5"));
        repository.save(buildVersion("agent-top", 3, "v3"));

        Optional<AgentVersion> latest = repository.findTopByAgentIdOrderByVersionDesc("agent-top");

        assertThat(latest).isPresent();
        assertThat(latest.get().getVersion()).isEqualTo(5);
        assertThat(latest.get().getChangeLog()).isEqualTo("v5");
    }

    @Test
    @DisplayName("findByAgentIdAndVersion 按 agentId+version 精确查询")
    void should_FindExactVersion_When_BothKeysMatch() {
        repository.save(buildVersion("agent-exact", 1, "first"));
        repository.save(buildVersion("agent-exact", 2, "second"));

        Optional<AgentVersion> found = repository.findByAgentIdAndVersion("agent-exact", 2);

        assertThat(found).isPresent();
        assertThat(found.get().getChangeLog()).isEqualTo("second");
    }

    @Test
    @DisplayName("existsByAgentIdAndVersion 检查 (agentId, version) 存在性")
    void should_CheckExistence_When_BothKeysProvided() {
        repository.save(buildVersion("agent-ex", 1, "v1"));

        assertThat(repository.existsByAgentIdAndVersion("agent-ex", 1)).isTrue();
        assertThat(repository.existsByAgentIdAndVersion("agent-ex", 2)).isFalse();
        assertThat(repository.existsByAgentIdAndVersion("other-agent", 1)).isFalse();
    }

    @Test
    @DisplayName("countByAgentId 统计 agent 版本数")
    void should_CountVersions_When_ByAgentId() {
        repository.save(buildVersion("agent-count", 1, "v1"));
        repository.save(buildVersion("agent-count", 2, "v2"));
        repository.save(buildVersion("agent-count", 3, "v3"));
        repository.save(buildVersion("other-agent", 1, "v1"));

        assertThat(repository.countByAgentId("agent-count")).isEqualTo(3);
        assertThat(repository.countByAgentId("other-agent")).isEqualTo(1);
        assertThat(repository.countByAgentId("nonexistent")).isEqualTo(0);
    }

    @Test
    @DisplayName("(agent_id, version) 唯一约束: 重复插入抛异常")
    void should_Throw_When_DuplicateAgentIdAndVersionInserted() {
        repository.save(buildVersion("dup-agent", 1, "v1"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                repository.save(buildVersion("dup-agent", 1, "duplicate"))
        ).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("@PrePersist 自动填充 createdAt")
    void should_AutoFillCreatedAt_When_Saved() {
        AgentVersion saved = repository.save(buildVersion("ts-agent", 1, "ts test"));

        assertThat(saved.getCreatedAt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("snapshot 字段持久化: 大文本 JSON 快照正确存取")
    void should_PersistSnapshot_When_LargeJsonSaved() {
        String bigSnapshot = "{\"agentId\":\"big-agent\",\"version\":1,\"systemPrompt\":\"" + "x".repeat(2000) + "\"}";
        AgentVersion v = buildVersion("big-agent", 1, "big snapshot");
        v.setSnapshot(bigSnapshot);
        repository.save(v);

        AgentVersion found = repository.findByAgentIdAndVersion("big-agent", 1).orElseThrow();

        assertThat(found.getSnapshot()).isEqualTo(bigSnapshot);
    }
}
