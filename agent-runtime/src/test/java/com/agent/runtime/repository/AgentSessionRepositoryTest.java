package com.agent.runtime.repository;

import com.agent.runtime.enums.SessionStatus;
import com.agent.runtime.model.AgentSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentSessionRepository 单元测试（doc 06 §7.1，T2）。
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional
class AgentSessionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AgentSessionRepository repository;

    @Test
    @DisplayName("findByAgentInstanceId: 按 agent_instance_id 查询")
    void findByAgentInstanceId() {
        repository.save(buildSession("ai_001", "sess_001", "task_001", SessionStatus.RUNNING));
        entityManager.flush();
        entityManager.clear();

        Optional<AgentSession> found = repository.findByAgentInstanceId("ai_001");

        assertThat(found).isPresent();
        assertThat(found.get().getSessionId()).isEqualTo("sess_001");
        assertThat(found.get().getTaskId()).isEqualTo("task_001");
        assertThat(found.get().getStatus()).isEqualTo(SessionStatus.RUNNING);
        assertThat(found.get().getMaxSteps()).isEqualTo(20);
        assertThat(found.get().getTokenBudget()).isEqualTo(32000);
    }

    @Test
    @DisplayName("findByTaskIdAndStatus: 按任务 + 状态查询")
    void findByTaskIdAndStatus() {
        repository.save(buildSession("ai_1", "sess_1", "task_1", SessionStatus.RUNNING));
        repository.save(buildSession("ai_2", "sess_2", "task_1", SessionStatus.SUCCESS));
        repository.save(buildSession("ai_3", "sess_3", "task_1", SessionStatus.RUNNING));
        entityManager.flush();
        entityManager.clear();

        List<AgentSession> running = repository.findByTaskIdAndStatus("task_1", SessionStatus.RUNNING);
        List<AgentSession> success = repository.findByTaskIdAndStatus("task_1", SessionStatus.SUCCESS);

        assertThat(running).hasSize(2);
        assertThat(success).hasSize(1);
        assertThat(success.get(0).getAgentInstanceId()).isEqualTo("ai_2");
    }

    @Test
    @DisplayName("findBySessionId: 按 session_id 查询")
    void findBySessionId() {
        repository.save(buildSession("ai_1", "sess_xyz", "task_1", SessionStatus.RUNNING));
        entityManager.flush();
        entityManager.clear();

        Optional<AgentSession> found = repository.findBySessionId("sess_xyz");

        assertThat(found).isPresent();
        assertThat(found.get().getAgentInstanceId()).isEqualTo("ai_1");
    }

    @Test
    @DisplayName("updateStatusByAgentInstanceId: 单条更新状态")
    void updateStatusByAgentInstanceId() {
        repository.save(buildSession("ai_1", "sess_1", "task_1", SessionStatus.RUNNING));
        entityManager.flush();
        entityManager.clear();

        int updated = repository.updateStatusByAgentInstanceId("ai_1", SessionStatus.PAUSED);
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        AgentSession s = repository.findByAgentInstanceId("ai_1").orElseThrow();
        assertThat(s.getStatus()).isEqualTo(SessionStatus.PAUSED);
    }

    @Test
    @DisplayName("findByTaskId: 按任务查询所有会话")
    void findByTaskId() {
        repository.save(buildSession("ai_1", "sess_1", "task_xyz", SessionStatus.RUNNING));
        repository.save(buildSession("ai_2", "sess_2", "task_xyz", SessionStatus.SUCCESS));
        entityManager.flush();
        entityManager.clear();

        List<AgentSession> list = repository.findByTaskId("task_xyz");

        assertThat(list).hasSize(2);
    }

    private AgentSession buildSession(String agentInstanceId, String sessionId, String taskId, SessionStatus status) {
        AgentSession s = new AgentSession();
        s.setAgentInstanceId(agentInstanceId);
        s.setSessionId(sessionId);
        s.setTenantId("tenant_test");
        s.setAgentId(1001L);
        s.setAgentVersion(1);
        s.setTaskId(taskId);
        s.setSubtaskId("sub_001");
        s.setNodeId("node_001");
        s.setStatus(status);
        s.setCurrentStepNumber(0);
        s.setMaxSteps(20);
        s.setTokenBudget(32000);
        s.setTokenUsed(0);
        s.setCostBudgetCent(10000L);
        s.setCostUsedCent(0L);
        s.setInputsJson("{\"input\":\"test\"}");
        s.setStartedAt(Instant.now());
        return s;
    }
}
