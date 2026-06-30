package com.agent.repo.repository;

import com.agent.repo.enums.AgentStatus;
import com.agent.repo.enums.AgentTier;
import com.agent.repo.model.AgentDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentDefinitionRepository JPA integration tests (Plan 08 T2).
 *
 * <p>Uses @DataJpaTest with H2 in-memory database (MODE=MySQL) to verify entity mapping,
 * JsonListConverter round-trip for List&lt;String&gt; columns, and repository query methods.</p>
 */
@DisplayName("AgentDefinitionRepository JPA 仓储测试")
@DataJpaTest
@ActiveProfiles("test")
class AgentDefinitionRepositoryTest {

    @Autowired
    private AgentDefinitionRepository repository;

    private AgentDefinition buildAgent(String agentId, String name, AgentStatus status, AgentTier tier) {
        AgentDefinition agent = new AgentDefinition(agentId, name);
        agent.setDescription("Agent " + name + " description");
        agent.setSystemPrompt("You are a helpful assistant.");
        agent.setStatus(status);
        agent.setAgentTier(tier);
        agent.setAbilityTags(Arrays.asList("code_generation", "qa"));
        agent.setBoundTools(Arrays.asList("tool-a", "tool-b"));
        agent.setBoundKnowledgeIds(Arrays.asList("kb-1"));
        return agent;
    }

    @Test
    @DisplayName("findByAgentId 按 agentId 精确查询返回 agent")
    void should_FindByAgentId_When_Exists() {
        repository.save(buildAgent("agent-001", "CodeBot", AgentStatus.PUBLISHED, AgentTier.STANDARD));

        Optional<AgentDefinition> found = repository.findByAgentId("agent-001");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("CodeBot");
        assertThat(found.get().getSystemPrompt()).isEqualTo("You are a helpful assistant.");
    }

    @Test
    @DisplayName("findByAgentId 查询不存在的 agentId 返回 empty")
    void should_ReturnEmpty_When_AgentIdNotFound() {
        Optional<AgentDefinition> found = repository.findByAgentId("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByAgentId 检查 agentId 存在性")
    void should_CheckExistence_When_ExistsByAgentId() {
        repository.save(buildAgent("agent-002", "ReviewBot", AgentStatus.DRAFT, AgentTier.LITE));

        assertThat(repository.existsByAgentId("agent-002")).isTrue();
        assertThat(repository.existsByAgentId("missing-id")).isFalse();
    }

    @Test
    @DisplayName("findByStatus 按状态过滤 agent")
    void should_FilterByStatus_When_FindByStatus() {
        repository.save(buildAgent("a1", "A1", AgentStatus.DRAFT, AgentTier.STANDARD));
        repository.save(buildAgent("a2", "A2", AgentStatus.PUBLISHED, AgentTier.STANDARD));
        repository.save(buildAgent("a3", "A3", AgentStatus.PUBLISHED, AgentTier.ADVANCED));

        List<AgentDefinition> published = repository.findByStatus(AgentStatus.PUBLISHED);

        assertThat(published).hasSize(2);
        assertThat(published).extracting(AgentDefinition::getAgentId)
                .containsExactlyInAnyOrder("a2", "a3");
    }

    @Test
    @DisplayName("findByStatusAndAgentTier 组合状态与等级过滤")
    void should_FilterByStatusAndTier_When_CombinedQuery() {
        repository.save(buildAgent("a1", "A1", AgentStatus.PUBLISHED, AgentTier.LITE));
        repository.save(buildAgent("a2", "A2", AgentStatus.PUBLISHED, AgentTier.ADVANCED));
        repository.save(buildAgent("a3", "A3", AgentStatus.DRAFT, AgentTier.ADVANCED));

        List<AgentDefinition> result = repository.findByStatusAndAgentTier(AgentStatus.PUBLISHED, AgentTier.ADVANCED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAgentId()).isEqualTo("a2");
    }

    @Test
    @DisplayName("agent_id 唯一约束: 重复插入抛异常")
    void should_Throw_When_DuplicateAgentIdInserted() {
        repository.save(buildAgent("dup-id", "First", AgentStatus.DRAFT, AgentTier.STANDARD));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                repository.save(buildAgent("dup-id", "Second", AgentStatus.DRAFT, AgentTier.STANDARD))
        ).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("@PrePersist 自动填充 createdAt 和 updatedAt")
    void should_AutoFillTimestamps_When_Saved() {
        AgentDefinition saved = repository.save(buildAgent("ts-001", "TS", AgentStatus.DRAFT, AgentTier.STANDARD));

        assertThat(saved.getCreatedAt()).isGreaterThan(0);
        assertThat(saved.getUpdatedAt()).isGreaterThan(0);
        assertThat(saved.getUpdatedAt()).isGreaterThanOrEqualTo(saved.getCreatedAt());
    }

    @Test
    @DisplayName("JsonListConverter List<String> 持久化往返: 保存后读回内容一致")
    void should_RoundTripListFields_When_SavedAndReadBack() {
        AgentDefinition agent = buildAgent("json-001", "JsonBot", AgentStatus.PUBLISHED, AgentTier.ADVANCED);
        agent.setAbilityTags(Arrays.asList("reasoning", "data_analysis", "qa"));
        agent.setBoundTools(Arrays.asList("search-tool", "calc-tool"));
        agent.setBoundKnowledgeIds(Arrays.asList("kb-1", "kb-2", "kb-3"));
        repository.save(agent);

        AgentDefinition found = repository.findByAgentId("json-001").orElseThrow();

        assertThat(found.getAbilityTags()).containsExactly("reasoning", "data_analysis", "qa");
        assertThat(found.getBoundTools()).containsExactly("search-tool", "calc-tool");
        assertThat(found.getBoundKnowledgeIds()).containsExactly("kb-1", "kb-2", "kb-3");
    }

    @Test
    @DisplayName("deleteByAgentId 按 agentId 删除并返回删除数")
    void should_DeleteByAgentId_When_Exists() {
        repository.save(buildAgent("del-001", "DelBot", AgentStatus.DRAFT, AgentTier.STANDARD));

        long deleted = repository.deleteByAgentId("del-001");

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.existsByAgentId("del-001")).isFalse();
    }
}
