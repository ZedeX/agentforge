package com.agent.runtime.repository;

import com.agent.runtime.enums.ReActPhaseType;
import com.agent.runtime.model.TokenUsageLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TokenUsageLogRepository 单元测试（doc 06 §7.1，T2）。
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional
class TokenUsageLogRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TokenUsageLogRepository repository;

    @Test
    @DisplayName("findBySessionId: 按会话查询用量日志")
    void findBySessionId() {
        repository.save(buildLog("ai_1", "sess_1", "step_1", ReActPhaseType.THINK, 100, 50, 150));
        repository.save(buildLog("ai_1", "sess_1", "step_2", ReActPhaseType.ACT, 200, 80, 280));
        repository.save(buildLog("ai_2", "sess_2", "step_3", ReActPhaseType.OBSERVE, 300, 100, 400));
        entityManager.flush();
        entityManager.clear();

        List<TokenUsageLog> list = repository.findBySessionId("sess_1");

        assertThat(list).hasSize(2);
        assertThat(list).allSatisfy(log -> assertThat(log.getSessionId()).isEqualTo("sess_1"));
    }

    @Test
    @DisplayName("findByAgentInstanceId: 按 agent_instance_id 查询")
    void findByAgentInstanceId() {
        repository.save(buildLog("ai_xyz", "sess_1", "step_1", ReActPhaseType.THINK, 100, 50, 150));
        repository.save(buildLog("ai_xyz", "sess_1", "step_2", ReActPhaseType.ACT, 200, 80, 280));
        repository.save(buildLog("ai_other", "sess_2", "step_3", ReActPhaseType.OBSERVE, 300, 100, 400));
        entityManager.flush();
        entityManager.clear();

        List<TokenUsageLog> list = repository.findByAgentInstanceId("ai_xyz");

        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("findByStepId: 按 step_id 查询")
    void findByStepId() {
        repository.save(buildLog("ai_1", "sess_1", "step_a", ReActPhaseType.THINK, 100, 50, 150));
        repository.save(buildLog("ai_1", "sess_1", "step_b", ReActPhaseType.ACT, 200, 80, 280));
        entityManager.flush();
        entityManager.clear();

        List<TokenUsageLog> list = repository.findByStepId("step_a");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTotalTokens()).isEqualTo(150);
    }

    @Test
    @DisplayName("sumTokensBySessionId: 聚合查询会话总 token 用量")
    void sumTokensBySessionId() {
        repository.save(buildLog("ai_1", "sess_1", "step_1", ReActPhaseType.THINK, 100, 50, 150));
        repository.save(buildLog("ai_1", "sess_1", "step_2", ReActPhaseType.ACT, 200, 80, 280));
        repository.save(buildLog("ai_2", "sess_2", "step_3", ReActPhaseType.OBSERVE, 300, 100, 400));
        entityManager.flush();
        entityManager.clear();

        long sum1 = repository.sumTokensBySessionId("sess_1");
        long sum2 = repository.sumTokensBySessionId("sess_2");
        long sumEmpty = repository.sumTokensBySessionId("not_exists");

        assertThat(sum1).isEqualTo(430L);
        assertThat(sum2).isEqualTo(400L);
        assertThat(sumEmpty).isEqualTo(0L);
    }

    @Test
    @DisplayName("sumTokensByAgentInstanceId: 聚合查询 agent_instance 总 token 用量")
    void sumTokensByAgentInstanceId() {
        repository.save(buildLog("ai_xyz", "sess_1", "step_1", ReActPhaseType.THINK, 100, 50, 150));
        repository.save(buildLog("ai_xyz", "sess_1", "step_2", ReActPhaseType.ACT, 200, 80, 280));
        entityManager.flush();
        entityManager.clear();

        long sum = repository.sumTokensByAgentInstanceId("ai_xyz");

        assertThat(sum).isEqualTo(430L);
    }

    private TokenUsageLog buildLog(String agentInstanceId, String sessionId, String stepId,
                                   ReActPhaseType phase, int prompt, int completion, int total) {
        TokenUsageLog log = new TokenUsageLog();
        log.setAgentInstanceId(agentInstanceId);
        log.setSessionId(sessionId);
        log.setStepId(stepId);
        log.setPhase(phase);
        log.setPromptTokens(prompt);
        log.setCompletionTokens(completion);
        log.setTotalTokens(total);
        log.setModel("gpt-4o");
        log.setCostCent(5L);
        return log;
    }
}
