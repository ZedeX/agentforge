package com.agent.runtime.repository;

import com.agent.runtime.enums.ReActPhaseType;
import com.agent.runtime.enums.ReflexionResult;
import com.agent.runtime.enums.StepStatus;
import com.agent.runtime.model.StepState;
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
 * StepStateRepository 单元测试（doc 06 §7.1，T2）。
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional
class StepStateRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private StepStateRepository repository;

    @Test
    @DisplayName("saveAndFindByStepId: 保存 StepState 后按 stepId 查询，断言字段完整")
    void saveAndFindByStepId() {
        StepState state = buildStepState("step_001", "ai_001", "sess_001", 1,
                ReActPhaseType.THINK, StepStatus.SUCCESS);
        repository.save(state);
        entityManager.flush();
        entityManager.clear();

        Optional<StepState> found = repository.findByStepId("step_001");

        assertThat(found).isPresent();
        assertThat(found.get().getAgentInstanceId()).isEqualTo("ai_001");
        assertThat(found.get().getSessionId()).isEqualTo("sess_001");
        assertThat(found.get().getStepNumber()).isEqualTo(1);
        assertThat(found.get().getPhase()).isEqualTo(ReActPhaseType.THINK);
        assertThat(found.get().getStatus()).isEqualTo(StepStatus.SUCCESS);
        assertThat(found.get().getThinkContent()).isEqualTo("analyze problem");
        assertThat(found.get().getTokensUsed()).isEqualTo(500);
        assertThat(found.get().getReflexionResult()).isEqualTo(ReflexionResult.RETRY);
    }

    @Test
    @DisplayName("findBySessionIdOrderByStepNumberAsc: 按会话查询并按 stepNumber 升序")
    void findBySessionIdOrderByStepNumberAsc() {
        repository.save(buildStepState("s3", "ai_1", "sess_1", 3, ReActPhaseType.OBSERVE, StepStatus.SUCCESS));
        repository.save(buildStepState("s1", "ai_1", "sess_1", 1, ReActPhaseType.THINK, StepStatus.SUCCESS));
        repository.save(buildStepState("s2", "ai_1", "sess_1", 2, ReActPhaseType.ACT, StepStatus.SUCCESS));
        entityManager.flush();
        entityManager.clear();

        List<StepState> list = repository.findBySessionIdOrderByStepNumberAsc("sess_1");

        assertThat(list).hasSize(3);
        assertThat(list.get(0).getStepNumber()).isEqualTo(1);
        assertThat(list.get(1).getStepNumber()).isEqualTo(2);
        assertThat(list.get(2).getStepNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("findBySessionIdAndStatus: 按会话 + 状态查询")
    void findBySessionIdAndStatus() {
        repository.save(buildStepState("s1", "ai_1", "sess_1", 1, ReActPhaseType.THINK, StepStatus.SUCCESS));
        repository.save(buildStepState("s2", "ai_1", "sess_1", 2, ReActPhaseType.ACT, StepStatus.FAILED));
        repository.save(buildStepState("s3", "ai_1", "sess_1", 3, ReActPhaseType.OBSERVE, StepStatus.SUCCESS));
        entityManager.flush();
        entityManager.clear();

        List<StepState> success = repository.findBySessionIdAndStatus("sess_1", StepStatus.SUCCESS);
        List<StepState> failed = repository.findBySessionIdAndStatus("sess_1", StepStatus.FAILED);

        assertThat(success).hasSize(2);
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0).getPhase()).isEqualTo(ReActPhaseType.ACT);
    }

    @Test
    @DisplayName("findLatestBySessionId: 取会话最新一步")
    void findLatestBySessionId() {
        repository.save(buildStepState("s1", "ai_1", "sess_1", 1, ReActPhaseType.THINK, StepStatus.SUCCESS));
        repository.save(buildStepState("s2", "ai_1", "sess_1", 2, ReActPhaseType.ACT, StepStatus.SUCCESS));
        repository.save(buildStepState("s3", "ai_1", "sess_1", 5, ReActPhaseType.OBSERVE, StepStatus.SUCCESS));
        entityManager.flush();
        entityManager.clear();

        Optional<StepState> latest = repository.findFirstBySessionIdOrderByStepNumberDesc("sess_1");

        assertThat(latest).isPresent();
        assertThat(latest.get().getStepNumber()).isEqualTo(5);
        assertThat(latest.get().getStepId()).isEqualTo("s3");
    }

    @Test
    @DisplayName("updateStatusByStepId: 单条更新状态")
    void updateStatusByStepId() {
        repository.save(buildStepState("s1", "ai_1", "sess_1", 1, ReActPhaseType.THINK, StepStatus.RUNNING));
        entityManager.flush();
        entityManager.clear();

        int updated = repository.updateStatusByStepId("s1", StepStatus.SUCCESS);
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        StepState s = repository.findByStepId("s1").orElseThrow();
        assertThat(s.getStatus()).isEqualTo(StepStatus.SUCCESS);
    }

    @Test
    @DisplayName("findFirstByAgentInstanceIdOrderByStepNumberDesc: 按 agent_instance_id 取最新")
    void findLatestByAgentInstanceId() {
        repository.save(buildStepState("s1", "ai_xyz", "sess_1", 1, ReActPhaseType.THINK, StepStatus.SUCCESS));
        repository.save(buildStepState("s2", "ai_xyz", "sess_1", 4, ReActPhaseType.ACT, StepStatus.SUCCESS));
        entityManager.flush();
        entityManager.clear();

        Optional<StepState> latest = repository.findFirstByAgentInstanceIdOrderByStepNumberDesc("ai_xyz");

        assertThat(latest).isPresent();
        assertThat(latest.get().getStepNumber()).isEqualTo(4);
    }

    private StepState buildStepState(String stepId, String agentInstanceId, String sessionId,
                                     int stepNumber, ReActPhaseType phase, StepStatus status) {
        StepState s = new StepState();
        s.setStepId(stepId);
        s.setAgentInstanceId(agentInstanceId);
        s.setSessionId(sessionId);
        s.setTenantId("tenant_test");
        s.setAgentDefinitionId(1001L);
        s.setTaskId("task_001");
        s.setStepNumber(stepNumber);
        s.setPhase(phase);
        s.setStatus(status);
        s.setThinkContent("analyze problem");
        s.setActionType("tool_call");
        s.setActionTarget("search_tool");
        s.setInputJson("{\"q\":\"test\"}");
        s.setOutputJson("{\"result\":\"ok\"}");
        s.setObserveContent("observed result");
        s.setTokensUsed(500);
        s.setCostCent(10L);
        s.setReflexionResult(ReflexionResult.RETRY);
        s.setStartedAt(Instant.now());
        s.setEndedAt(Instant.now());
        s.setDurationMs(100L);
        s.setErrorMessage(null);
        return s;
    }
}
