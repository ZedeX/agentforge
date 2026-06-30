package com.agent.repo.api.impl;

import com.agent.repo.model.AgentRating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentRatingServiceImpl unit tests (doc 06-agent-repo §3.2).
 */
@DisplayName("AgentRatingServiceImpl Agent 评分服务")
class AgentRatingServiceImplTest {

    private final AgentRatingServiceImpl service = new AgentRatingServiceImpl();

    private AgentRating rating(String agentId, String userId, int score, String comment) {
        return new AgentRating(agentId, userId, score, comment);
    }

    @Test
    @DisplayName("submit 后能按 agentId 列出该评分")
    void should_FindByAgent_When_Submitted() {
        AgentRating saved = service.submit(rating("agent-1", "user-1", 4, "good"));
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getCreatedAt()).isPositive();
        List<AgentRating> ratings = service.findByAgent("agent-1");
        assertThat(ratings).hasSize(1);
        assertThat(ratings.get(0).getScore()).isEqualTo(4);
        assertThat(ratings.get(0).getComment()).isEqualTo("good");
    }

    @Test
    @DisplayName("submit 多次后 getAverageScore 返回算术平均")
    void should_CalcAverage_When_MultipleRatings() {
        service.submit(rating("agent-1", "u1", 5, "great"));
        service.submit(rating("agent-1", "u2", 3, "ok"));
        service.submit(rating("agent-1", "u3", 4, "good"));
        // (5 + 3 + 4) / 3 = 4.0
        assertThat(service.getAverageScore("agent-1")).isEqualTo(4.0);
        assertThat(service.countRatings("agent-1")).isEqualTo(3);
    }

    @Test
    @DisplayName("无评分的 agent getAverageScore 返回 0.0")
    void should_ReturnZero_When_NoRatings() {
        assertThat(service.getAverageScore("unknown")).isZero();
        assertThat(service.countRatings("unknown")).isZero();
        assertThat(service.findByAgent("unknown")).isEmpty();
    }

    @Test
    @DisplayName("score < 1 被钳制到 1")
    void should_ClampToMin_When_ScoreBelowMin() {
        AgentRating saved = service.submit(rating("agent-1", "u1", 0, "low"));
        assertThat(saved.getScore()).isEqualTo(1);
    }

    @Test
    @DisplayName("score > 5 被钳制到 5")
    void should_ClampToMax_When_ScoreAboveMax() {
        AgentRating saved = service.submit(rating("agent-1", "u1", 10, "high"));
        assertThat(saved.getScore()).isEqualTo(5);
    }

    @Test
    @DisplayName("score 在 [1,5] 范围内不被修改")
    void should_KeepScore_When_WithinRange() {
        for (int s = 1; s <= 5; s++) {
            AgentRating r = service.submit(rating("agent-range", "u" + s, s, "score=" + s));
            assertThat(r.getScore()).isEqualTo(s);
        }
        assertThat(service.getAverageScore("agent-range")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("不同 agentId 的评分互不影响")
    void should_IsolateByAgentId_When_DifferentAgents() {
        service.submit(rating("agent-1", "u1", 5, ""));
        service.submit(rating("agent-2", "u2", 3, ""));
        assertThat(service.countRatings("agent-1")).isEqualTo(1);
        assertThat(service.countRatings("agent-2")).isEqualTo(1);
        assertThat(service.getAverageScore("agent-1")).isEqualTo(5.0);
        assertThat(service.getAverageScore("agent-2")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("submit null rating 或 null agentId 抛 IllegalArgumentException")
    void should_Throw_When_SubmitNullOrNullId() {
        assertThatThrownBy(() -> service.submit(null))
                .isInstanceOf(IllegalArgumentException.class);
        AgentRating nullAgent = new AgentRating(null, "u1", 5, "");
        assertThatThrownBy(() -> service.submit(nullAgent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("findByAgent / getAverageScore / countRatings null 入参安全返回")
    void should_SafeReturn_When_NullAgentIdPassed() {
        assertThat(service.findByAgent(null)).isEmpty();
        assertThat(service.getAverageScore(null)).isZero();
        assertThat(service.countRatings(null)).isZero();
    }

    @Test
    @DisplayName("findByAgent 返回副本, 修改不影响 store")
    void should_ReturnCopy_When_FindByAgentCalled() {
        service.submit(rating("agent-1", "u1", 5, ""));
        List<AgentRating> ratings = service.findByAgent("agent-1");
        ratings.clear();
        assertThat(service.findByAgent("agent-1")).hasSize(1);
    }
}
