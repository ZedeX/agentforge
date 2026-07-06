package com.agent.repo.integration;

import com.agent.repo.enums.AgentStatus;
import com.agent.repo.enums.AgentTier;
import com.agent.repo.model.AgentDefinition;
import com.agent.repo.repository.AgentDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E2E integration test: agent-repo JPA against real MySQL 8.0.36 via Testcontainers.
 *
 * <p>Validates DDL compatibility, unique constraint enforcement (uk_agent_id),
 * JSON column round-trip via {@link com.agent.repo.config.JsonListConverter},
 * and derived query methods that H2 in MySQL mode cannot faithfully emulate.</p>
 *
 * <p>Skipped automatically when Docker is unavailable
 * ({@code @Testcontainers(disabledWithoutDocker = true)}).</p>
 *
 * <p>Run via: {@code mvn -Pe2e-perf -pl agent-repo test -Dtest=AgentRepoJpaTestcontainersTest}</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AgentRepoJpaTestcontainersTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("agent_repo")
            .withUsername("root")
            .withPassword("root")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private AgentDefinitionRepository repository;

    @Test
    @DisplayName("保存 AgentDefinition 后应能按 agentId 查询到完整字段")
    void should_PersistAndFindByAgentId_When_AllFieldsPopulated() {
        AgentDefinition agent = new AgentDefinition("agt_tc_001", "Industry Research Agent");
        agent.setDescription("Industry research agent backed by gpt-4o and claude-3.5-sonnet");
        agent.setAbilityTags(Arrays.asList("research", "analysis", "report"));
        agent.setSystemPrompt("You are a senior industry analyst. Always cite sources.");
        agent.setAgentTier(AgentTier.ADVANCED);
        agent.setMaxSteps(20);
        agent.setMaxToken(8192);
        agent.setStatus(AgentStatus.DRAFT);
        agent.setBoundTools(Arrays.asList("web_search", "arxiv_fetch"));
        agent.setBoundKnowledgeIds(Arrays.asList("kb_industry_001", "kb_paper_001"));

        repository.save(agent);

        Optional<AgentDefinition> found = repository.findByAgentId("agt_tc_001");
        assertThat(found).isPresent();
        AgentDefinition loaded = found.get();
        assertThat(loaded.getName()).isEqualTo("Industry Research Agent");
        assertThat(loaded.getAgentTier()).isEqualTo(AgentTier.ADVANCED);
        assertThat(loaded.getStatus()).isEqualTo(AgentStatus.DRAFT);
        assertThat(loaded.getAbilityTags()).containsExactly("research", "analysis", "report");
        assertThat(loaded.getBoundTools()).containsExactly("web_search", "arxiv_fetch");
        assertThat(loaded.getBoundKnowledgeIds()).containsExactly("kb_industry_001", "kb_paper_001");
        assertThat(loaded.getMaxSteps()).isEqualTo(20);
        assertThat(loaded.getMaxToken()).isEqualTo(8192);
        assertThat(loaded.getVersion()).isEqualTo(1);
        assertThat(loaded.getCreatedAt()).isGreaterThan(0);
        assertThat(loaded.getUpdatedAt()).isGreaterThanOrEqualTo(loaded.getCreatedAt());
    }

    @Test
    @DisplayName("重复 agentId 应触发 uk_agent_id 唯一约束违反")
    void should_ThrowDataIntegrityViolation_When_DuplicateAgentIdInserted() {
        AgentDefinition first = new AgentDefinition("agt_dup_001", "First");
        first.setDescription("d");
        first.setSystemPrompt("p");
        repository.save(first);

        AgentDefinition duplicate = new AgentDefinition("agt_dup_001", "Duplicate");
        duplicate.setDescription("d2");
        duplicate.setSystemPrompt("p2");

        // flush to trigger INSERT and constraint check at DB level
        assertThatThrownBy(() -> {
            repository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("按 status 查询应返回所有匹配状态的 Agent 列表")
    void should_FindByStatus_When_MultipleAgentsInSameStatus() {
        AgentDefinition a1 = newAgent("agt_s_001", "A1", AgentStatus.DRAFT);
        AgentDefinition a2 = newAgent("agt_s_002", "A2", AgentStatus.PUBLISHED);
        AgentDefinition a3 = newAgent("agt_s_003", "A3", AgentStatus.DRAFT);
        repository.saveAll(Arrays.asList(a1, a2, a3));

        List<AgentDefinition> drafts = repository.findByStatus(AgentStatus.DRAFT);

        assertThat(drafts).extracting(AgentDefinition::getAgentId)
                .containsExactlyInAnyOrder("agt_s_001", "agt_s_003");
    }

    @Test
    @DisplayName("按 agentTier 查询应正确过滤 Agent 层级")
    void should_FindByAgentTier_When_TierFilterApplied() {
        AgentDefinition lite = newAgentWithTier("agt_t_001", "Lite", AgentTier.LITE);
        AgentDefinition standard = newAgentWithTier("agt_t_002", "Standard", AgentTier.STANDARD);
        AgentDefinition advanced = newAgentWithTier("agt_t_003", "Advanced", AgentTier.ADVANCED);
        repository.saveAll(Arrays.asList(lite, standard, advanced));

        List<AgentDefinition> advancedOnly = repository.findByAgentTier(AgentTier.ADVANCED);

        assertThat(advancedOnly).hasSize(1);
        assertThat(advancedOnly.get(0).getAgentId()).isEqualTo("agt_t_003");
    }

    @Test
    @DisplayName("existsByAgentId 应在持久化后返回 true，未持久化的返回 false")
    void should_CheckExistence_When_ExistsByAgentIdCalled() {
        repository.save(newAgent("agt_exist_001", "Exist", AgentStatus.DRAFT));

        assertThat(repository.existsByAgentId("agt_exist_001")).isTrue();
        assertThat(repository.existsByAgentId("agt_not_exist_999")).isFalse();
    }

    private AgentDefinition newAgent(String agentId, String name, AgentStatus status) {
        AgentDefinition a = new AgentDefinition(agentId, name);
        a.setDescription("desc for " + name);
        a.setSystemPrompt("prompt for " + name);
        a.setStatus(status);
        return a;
    }

    private AgentDefinition newAgentWithTier(String agentId, String name, AgentTier tier) {
        AgentDefinition a = new AgentDefinition(agentId, name);
        a.setDescription("desc for " + name);
        a.setSystemPrompt("prompt for " + name);
        a.setStatus(AgentStatus.DRAFT);
        a.setAgentTier(tier);
        return a;
    }
}
