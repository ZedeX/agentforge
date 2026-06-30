package com.agent.repo.repository;

import com.agent.repo.model.AgentRating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentRatingRepository JPA integration tests (Plan 08 T4).
 *
 * <p>Verifies rating persistence, recency ordering, user-filtered lookup,
 * average score aggregation and count queries.</p>
 */
@DisplayName("AgentRatingRepository JPA 仓储测试")
@DataJpaTest
@ActiveProfiles("test")
class AgentRatingRepositoryTest {

    @Autowired
    private AgentRatingRepository repository;

    private AgentRating buildRating(String agentId, String userId, int score, String comment) {
        return new AgentRating(agentId, userId, score, comment);
    }

    @Test
    @DisplayName("findByAgentIdOrderByCreatedAtDesc 按创建时间降序返回评分")
    void should_ReturnRatingsDescByTime_When_FindByAgentId() {
        repository.save(buildRating("agent-r", "user-1", 5, "great"));
        repository.save(buildRating("agent-r", "user-2", 3, "ok"));
        repository.save(buildRating("agent-r", "user-3", 1, "bad"));

        List<AgentRating> ratings = repository.findByAgentIdOrderByCreatedAtDesc("agent-r");

        assertThat(ratings).hasSize(3);
        // 所有评分都属于该 agent
        assertThat(ratings).allMatch(r -> "agent-r".equals(r.getAgentId()));
    }

    @Test
    @DisplayName("findByAgentIdAndUserId 按 agent+user 过滤评分")
    void should_FilterByAgentAndUser_When_BothProvided() {
        repository.save(buildRating("agent-a", "user-x", 5, "good"));
        repository.save(buildRating("agent-a", "user-y", 2, "poor"));
        repository.save(buildRating("agent-b", "user-x", 4, "nice"));

        List<AgentRating> ratings = repository.findByAgentIdAndUserId("agent-a", "user-x");

        assertThat(ratings).hasSize(1);
        assertThat(ratings.get(0).getScore()).isEqualTo(5);
    }

    @Test
    @DisplayName("countByAgentId 统计 agent 评分总数")
    void should_CountRatings_When_ByAgentId() {
        repository.save(buildRating("agent-c", "u1", 5, ""));
        repository.save(buildRating("agent-c", "u2", 4, ""));
        repository.save(buildRating("agent-c", "u3", 3, ""));
        repository.save(buildRating("agent-d", "u1", 5, ""));

        assertThat(repository.countByAgentId("agent-c")).isEqualTo(3);
        assertThat(repository.countByAgentId("agent-d")).isEqualTo(1);
        assertThat(repository.countByAgentId("none")).isEqualTo(0);
    }

    @Test
    @DisplayName("avgScoreByAgentId 计算 agent 平均分")
    void should_CalcAverage_When_ByAgentId() {
        repository.save(buildRating("avg-agent", "u1", 5, ""));
        repository.save(buildRating("avg-agent", "u2", 3, ""));
        repository.save(buildRating("avg-agent", "u3", 4, ""));

        double avg = repository.avgScoreByAgentId("avg-agent");

        assertThat(avg).isCloseTo(4.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("avgScoreByAgentId 无评分时返回 0.0")
    void should_ReturnZeroAvg_When_NoRatings() {
        double avg = repository.avgScoreByAgentId("no-ratings");

        assertThat(avg).isEqualTo(0.0);
    }

    @Test
    @DisplayName("@PrePersist 自动填充 createdAt")
    void should_AutoFillCreatedAt_When_Saved() {
        AgentRating saved = repository.save(buildRating("ts-agent", "ts-user", 5, "ts"));

        assertThat(saved.getCreatedAt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("评分持久化: score 和 comment 正确存取")
    void should_PersistScoreAndComment_When_Saved() {
        repository.save(buildRating("persist-agent", "u1", 4, "very helpful agent"));

        AgentRating found = repository.findByAgentIdAndUserId("persist-agent", "u1").get(0);

        assertThat(found.getScore()).isEqualTo(4);
        assertThat(found.getComment()).isEqualTo("very helpful agent");
    }
}
