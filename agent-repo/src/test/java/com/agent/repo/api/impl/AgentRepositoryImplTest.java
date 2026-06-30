package com.agent.repo.api.impl;

import com.agent.repo.enums.AgentStatus;
import com.agent.repo.enums.AgentTier;
import com.agent.repo.model.AgentDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentRepositoryImpl unit tests (doc 06-agent-repo §4.1).
 */
@DisplayName("AgentRepositoryImpl Agent 仓库")
class AgentRepositoryImplTest {

    private final AgentRepositoryImpl repo = new AgentRepositoryImpl();

    private AgentDefinition sample(String id, String name) {
        AgentDefinition a = new AgentDefinition(id, name);
        a.setAgentTier(AgentTier.STANDARD);
        a.setStatus(AgentStatus.DRAFT);
        a.setVersion(1);
        return a;
    }

    @Test
    @DisplayName("save 后能按 agentId 查询到完整字段")
    void should_FindById_When_Saved() {
        AgentDefinition saved = repo.save(sample("agent-1", "Agent One"));
        Optional<AgentDefinition> found = repo.findById("agent-1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Agent One");
        assertThat(saved.getCreatedAt()).isPositive();
        assertThat(saved.getUpdatedAt()).isPositive();
    }

    @Test
    @DisplayName("findByName 大小写敏感精确匹配")
    void should_FindByName_When_ExactMatch() {
        repo.save(sample("agent-1", "Agent One"));
        assertThat(repo.findByName("Agent One")).isPresent();
        assertThat(repo.findByName("agent one")).isEmpty();
    }

    @Test
    @DisplayName("existsByAgentId / existsByName 反映存在性")
    void should_ReflectExistence_When_ExistsCalled() {
        repo.save(sample("agent-1", "Agent One"));
        assertThat(repo.existsByAgentId("agent-1")).isTrue();
        assertThat(repo.existsByAgentId("agent-2")).isFalse();
        assertThat(repo.existsByName("Agent One")).isTrue();
        assertThat(repo.existsByName("Unknown")).isFalse();
    }

    @Test
    @DisplayName("deleteById 删除已存在的 agent, 不存在的 id 返回 false")
    void should_DeleteAndReturnTrue_When_Exists() {
        repo.save(sample("agent-1", "Agent One"));
        assertThat(repo.deleteById("agent-1")).isTrue();
        assertThat(repo.findById("agent-1")).isEmpty();
        assertThat(repo.deleteById("agent-1")).isFalse();
        assertThat(repo.deleteById(null)).isFalse();
    }

    @Test
    @DisplayName("findAll 返回全部 agent 副本")
    void should_ReturnAll_When_FindAllCalled() {
        repo.save(sample("agent-1", "Agent One"));
        repo.save(sample("agent-2", "Agent Two"));
        List<AgentDefinition> all = repo.findAll();
        assertThat(all).hasSize(2);
        // mutate returned list, should not affect store
        all.clear();
        assertThat(repo.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("count 反映 store 当前容量")
    void should_ReturnCount_When_CountCalled() {
        assertThat(repo.count()).isZero();
        repo.save(sample("agent-1", "Agent One"));
        assertThat(repo.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("save 同一 agentId 第二次为 update 而非 insert")
    void should_Update_When_SaveSameIdTwice() {
        repo.save(sample("agent-1", "Agent One"));
        AgentDefinition updated = sample("agent-1", "Agent One Updated");
        repo.save(updated);
        assertThat(repo.count()).isEqualTo(1);
        assertThat(repo.findById("agent-1").get().getName()).isEqualTo("Agent One Updated");
    }

    @Test
    @DisplayName("save null agent 或 null/empty agentId 抛 IllegalArgumentException")
    void should_Throw_When_SaveNullOrNullId() {
        assertThatThrownBy(() -> repo.save(null))
                .isInstanceOf(IllegalArgumentException.class);
        AgentDefinition nullId = new AgentDefinition();
        assertThatThrownBy(() -> repo.save(nullId))
                .isInstanceOf(IllegalArgumentException.class);
        AgentDefinition emptyId = new AgentDefinition("", "Empty");
        assertThatThrownBy(() -> repo.save(emptyId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("findById / findByName null 入参返回 empty 而非异常")
    void should_ReturnEmpty_When_NullLookup() {
        assertThat(repo.findById(null)).isEmpty();
        assertThat(repo.findByName(null)).isEmpty();
    }

    @Test
    @DisplayName("重 save 保留首次 createdAt, 仅更新 updatedAt")
    void should_PreserveCreatedAt_When_ReSaved() throws InterruptedException {
        AgentDefinition first = repo.save(sample("agent-1", "Agent One"));
        long created = first.getCreatedAt();
        Thread.sleep(2);
        AgentDefinition second = repo.save(sample("agent-1", "Agent One v2"));
        assertThat(second.getCreatedAt()).isEqualTo(created);
        assertThat(second.getUpdatedAt()).isGreaterThanOrEqualTo(created);
    }
}
