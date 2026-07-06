package com.agent.tool.engine.integration;

import com.agent.tool.engine.entity.ToolCallLogEntity;
import com.agent.tool.engine.entity.ToolRegistryEntity;
import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.ToolType;
import com.agent.tool.engine.repository.ToolCallLogRepository;
import com.agent.tool.engine.repository.ToolRegistryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E2E integration test: agent-tool-engine JPA against real MySQL 8.0.36 via Testcontainers.
 *
 * <p>Validates DDL compatibility for {@code tool_registry} + {@code tool_call_log} tables,
 * MySQL native JSON column round-trip (input_schema / output_schema / scene_tags /
 * ability_tags / error_codes), composite unique constraint {@code uk_name_version},
 * single-column unique constraint {@code uk_tool_id}, and JPA {@code @Version}
 * optimistic locking that H2 cannot faithfully emulate.</p>
 *
 * <p>Skipped automatically when Docker is unavailable.</p>
 *
 * <p>Run via: {@code mvn -Pe2e-perf -pl agent-tool-engine test -Dtest=ToolEngineJpaTestcontainersTest}</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ToolEngineJpaTestcontainersTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("agent_tool")
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
    private ToolRegistryRepository registryRepository;

    @Autowired
    private ToolCallLogRepository callLogRepository;

    @Test
    @DisplayName("保存 ToolRegistryEntity 后应能按 toolId 查询到所有 JSON 列完整内容")
    void should_PersistToolRegistry_When_AllJsonColumnsPopulated() {
        ToolRegistryEntity tool = newTool("tool_tc_001", "web_search", 1);

        registryRepository.saveAndFlush(tool);

        Optional<ToolRegistryEntity> found = registryRepository.findByToolId("tool_tc_001");
        assertThat(found).isPresent();
        ToolRegistryEntity loaded = found.get();
        assertThat(loaded.getName()).isEqualTo("web_search");
        assertThat(loaded.getDisplayName()).isEqualTo("Web Search");
        assertThat(loaded.getToolType()).isEqualTo(ToolType.ATOMIC);
        assertThat(loaded.getExecutorType()).isEqualTo(ExecutorType.HTTP_API);
        assertThat(loaded.getRiskLevel()).isEqualTo(1);
        assertThat(loaded.getSceneTags()).contains("research");
        assertThat(loaded.getAbilityTags()).contains("search");
        assertThat(loaded.getInputSchema()).contains("query");
        assertThat(loaded.getOutputSchema()).contains("results");
        assertThat(loaded.getErrorCodes()).contains("RATE_LIMITED");
        assertThat(loaded.getVersionLock()).isZero();
    }

    @Test
    @DisplayName("重复 toolId 应触发 uk_tool_id 唯一约束违反")
    void should_ThrowDataIntegrityViolation_When_DuplicateToolIdInserted() {
        registryRepository.saveAndFlush(newTool("tool_dup_001", "dup_tool", 1));

        ToolRegistryEntity dup = newTool("tool_dup_001", "another_name", 1);

        assertThatThrownBy(() -> registryRepository.saveAndFlush(dup))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("同 name 同 version 应触发 uk_name_version 唯一约束违反，不同 version 允许")
    void should_EnforceNameVersionUniqueConstraint_When_DuplicateNameVersionInserted() {
        // same name, different version -> allowed
        ToolRegistryEntity v1 = newTool("tool_nv_001", "arxiv_fetch", 1);
        ToolRegistryEntity v2 = newTool("tool_nv_002", "arxiv_fetch", 2);
        registryRepository.saveAndFlush(v1);
        registryRepository.saveAndFlush(v2);
        assertThat(registryRepository.existsByToolId("tool_nv_001")).isTrue();
        assertThat(registryRepository.existsByToolId("tool_nv_002")).isTrue();

        // same name, same version -> rejected
        ToolRegistryEntity dup = newTool("tool_nv_003", "arxiv_fetch", 1);
        assertThatThrownBy(() -> registryRepository.saveAndFlush(dup))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("@Version 乐观锁：并发修改同条记录应抛 OptimisticLockingFailureException")
    void should_ThrowOptimisticLockException_When_ConcurrentModificationOccurs() {
        ToolRegistryEntity saved = registryRepository.saveAndFlush(newTool("tool_lock_001", "lock_tool", 1));

        // Simulate two threads loading the same version
        ToolRegistryEntity copyA = registryRepository.findById(saved.getId()).orElseThrow();
        ToolRegistryEntity copyB = registryRepository.findById(saved.getId()).orElseThrow();

        // A updates first -> version_lock 0 -> 1
        copyA.setAvgCostCent(100L);
        registryRepository.saveAndFlush(copyA);

        // B tries to update with stale version_lock 0 -> should fail
        copyB.setAvgCostCent(200L);
        assertThatThrownBy(() -> registryRepository.saveAndFlush(copyB))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class)
                .hasMessageContaining("ToolRegistryEntity");
    }

    @Test
    @DisplayName("保存 ToolCallLogEntity 后应能按 callId 查询到完整审计记录")
    void should_PersistToolCallLog_When_AllAuditFieldsPopulated() {
        ToolCallLogEntity log = new ToolCallLogEntity();
        log.setCallId("call_tc_001");
        log.setTaskId("task_001");
        log.setStepNo(1);
        log.setAgentId(1001L);
        log.setToolId("tool_tc_001");
        log.setToolVersion(1);
        log.setInput("{\"query\":\"JDK 17 ZGC\"}");
        log.setOutput("{\"results\":[\"...\",\"...\"]}");
        log.setStatus("SUCCESS");
        log.setDurationMs(150);
        log.setCostCent(5L);
        log.setTokenUsed(120);
        log.setRiskLevel(1);
        log.setTraceId("trace_001");
        log.setTenantId("tn_001");
        log.setParamsHash("hash_001");
        log.setCacheHit(false);

        callLogRepository.saveAndFlush(log);

        Optional<ToolCallLogEntity> found = callLogRepository.findByCallId("call_tc_001");
        assertThat(found).isPresent();
        ToolCallLogEntity loaded = found.get();
        assertThat(loaded.getToolId()).isEqualTo("tool_tc_001");
        assertThat(loaded.getStatus()).isEqualTo("SUCCESS");
        assertThat(loaded.getDurationMs()).isEqualTo(150);
        assertThat(loaded.getInput()).contains("ZGC");
        assertThat(loaded.getOutput()).contains("results");
    }

    private ToolRegistryEntity newTool(String toolId, String name, int version) {
        ToolRegistryEntity t = new ToolRegistryEntity();
        t.setToolId(toolId);
        t.setName(name);
        t.setDisplayName(name.substring(0, 1).toUpperCase() + name.substring(1));
        t.setDescription("Test tool: " + name);
        t.setSceneTags("[\"research\",\"analysis\"]");
        t.setAbilityTags("[\"search\",\"fetch\"]");
        t.setToolType(ToolType.ATOMIC);
        t.setRiskLevel(1);
        t.setInputSchema("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}");
        t.setOutputSchema("{\"type\":\"object\",\"properties\":{\"results\":{\"type\":\"array\"}}}");
        t.setErrorCodes("[\"RATE_LIMITED\",\"TIMEOUT\"]");
        t.setExecutorType(ExecutorType.HTTP_API);
        t.setEndpoint("https://api.example.com/search");
        t.setTimeoutMs(5000);
        t.setVersion(version);
        t.setStatus(2);
        return t;
    }
}
