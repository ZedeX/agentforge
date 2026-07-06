package com.agent.orchestrator.integration;

import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E2E integration test: agent-task-orchestrator DAG JPA against real MySQL 8.0.36
 * via Testcontainers.
 *
 * <p>Validates DDL compatibility for {@code dag_node} + {@code dag_edge} tables,
 * JSON column round-trip (ability_tags / inputs / outputs / param_mapping),
 * composite unique constraints {@code uk_dag_node_id} and {@code uk_dag_edge}.
 * Uses {@link EntityManager} directly because DagNode/DagEdge do not have dedicated
 * Spring Data repositories (they are value objects backed by JPA entities).</p>
 *
 * <p>Skipped automatically when Docker is unavailable.</p>
 *
 * <p>Run via: {@code mvn -Pe2e-perf -pl agent-task-orchestrator test -Dtest=TaskDagTestcontainersTest}</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskDagTestcontainersTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("agent_task")
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

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("保存 DagNode 后应能按 nodeId 查询到 JSON 列完整内容")
    void should_PersistDagNode_When_AllJsonColumnsPopulated() {
        DagNode node = DagNode.builder()
                .dagId(1001L)
                .nodeId("node_tc_001")
                .nodeType("TASK")
                .subtaskId("sub_001")
                .title("Research industry trends")
                .agentId(2001L)
                .abilityTags("[\"research\",\"analysis\",\"report\"]")
                .inputs("{\"topic\":\"AI agent platforms\",\"depth\":\"deep\"}")
                .outputs("{\"report\":\"markdown\",\"citations\":\"list\"}")
                .status("pending")
                .build();

        em.persist(node);
        em.flush();
        em.clear();

        List<DagNode> nodes = em.createQuery(
                "SELECT n FROM DagNode n WHERE n.dagId = :dagId", DagNode.class)
                .setParameter("dagId", 1001L)
                .getResultList();

        assertThat(nodes).hasSize(1);
        DagNode loaded = nodes.get(0);
        assertThat(loaded.getNodeId()).isEqualTo("node_tc_001");
        assertThat(loaded.getNodeType()).isEqualTo("TASK");
        assertThat(loaded.getTitle()).isEqualTo("Research industry trends");
        assertThat(loaded.getAbilityTags()).isEqualTo("[\"research\",\"analysis\",\"report\"]");
        assertThat(loaded.getInputs()).contains("AI agent platforms");
        assertThat(loaded.getOutputs()).contains("markdown");
        assertThat(loaded.getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("同一 dagId 下重复 nodeId 应触发 uk_dag_node_id 唯一约束违反")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void should_ThrowDataIntegrityViolation_When_DuplicateNodeIdInSameDag() {
        // Outer transaction suspended so constraint violation is not swallowed by test rollback.
        DagNode first = DagNode.builder()
                .dagId(2001L).nodeId("dup_001").nodeType("TASK")
                .title("first").status("pending").build();
        em.persist(first);
        em.flush();

        DagNode dup = DagNode.builder()
                .dagId(2001L).nodeId("dup_001").nodeType("TASK")
                .title("duplicate").status("pending").build();

        assertThatThrownBy(() -> {
            em.persist(dup);
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("不同 dagId 下相同 nodeId 应允许（uk 仅约束 dagId+nodeId 组合）")
    void should_AllowSameNodeId_When_DifferentDagId() {
        DagNode n1 = DagNode.builder()
                .dagId(3001L).nodeId("shared_001").nodeType("TASK")
                .title("in dag 3001").status("pending").build();
        DagNode n2 = DagNode.builder()
                .dagId(3002L).nodeId("shared_001").nodeType("TASK")
                .title("in dag 3002").status("pending").build();

        em.persist(n1);
        em.persist(n2);
        em.flush();
        em.clear();

        Long cnt1 = em.createQuery(
                "SELECT COUNT(n) FROM DagNode n WHERE n.dagId = :dagId", Long.class)
                .setParameter("dagId", 3001L).getSingleResult();
        Long cnt2 = em.createQuery(
                "SELECT COUNT(n) FROM DagNode n WHERE n.dagId = :dagId", Long.class)
                .setParameter("dagId", 3002L).getSingleResult();

        assertThat(cnt1).isEqualTo(1L);
        assertThat(cnt2).isEqualTo(1L);
    }

    @Test
    @DisplayName("保存 DagEdge 后应能按 dagId 查询边集合，验证 paramMapping JSON 列")
    void should_PersistDagEdge_When_ParamMappingJsonStored() {
        DagNode parent = DagNode.builder()
                .dagId(4001L).nodeId("parent_001").nodeType("TASK")
                .title("parent").status("pending").build();
        DagNode child = DagNode.builder()
                .dagId(4001L).nodeId("child_001").nodeType("TASK")
                .title("child").status("pending").build();
        em.persist(parent);
        em.persist(child);

        DagEdge edge = DagEdge.builder()
                .dagId(4001L)
                .parentNodeId("parent_001")
                .childNodeId("child_001")
                .edgeType("DATA")
                .paramMapping("{\"report\":\"inputs.report\",\"citations\":\"inputs.citations\"}")
                .build();
        em.persist(edge);
        em.flush();
        em.clear();

        List<DagEdge> edges = em.createQuery(
                "SELECT e FROM DagEdge e WHERE e.dagId = :dagId", DagEdge.class)
                .setParameter("dagId", 4001L)
                .getResultList();

        assertThat(edges).hasSize(1);
        DagEdge loaded = edges.get(0);
        assertThat(loaded.getParentNodeId()).isEqualTo("parent_001");
        assertThat(loaded.getChildNodeId()).isEqualTo("child_001");
        assertThat(loaded.getEdgeType()).isEqualTo("DATA");
        assertThat(loaded.getParamMapping()).contains("report");
        assertThat(loaded.getParamMapping()).contains("citations");
    }

    @Test
    @DisplayName("重复 (dagId, parentNodeId, childNodeId) 应触发 uk_dag_edge 唯一约束违反")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void should_ThrowDataIntegrityViolation_When_DuplicateEdgeInserted() {
        DagNode p = DagNode.builder()
                .dagId(5001L).nodeId("p").nodeType("TASK").title("p").status("pending").build();
        DagNode c = DagNode.builder()
                .dagId(5001L).nodeId("c").nodeType("TASK").title("c").status("pending").build();
        em.persist(p);
        em.persist(c);
        em.flush();

        DagEdge first = DagEdge.builder()
                .dagId(5001L).parentNodeId("p").childNodeId("c")
                .edgeType("DATA").build();
        em.persist(first);
        em.flush();

        DagEdge dup = DagEdge.builder()
                .dagId(5001L).parentNodeId("p").childNodeId("c")
                .edgeType("LOGIC").build();

        assertThatThrownBy(() -> {
            em.persist(dup);
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
