package com.agent.repo.repository;

import com.agent.repo.model.AgentRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link AgentRating} (Plan 08 T4).
 *
 * <p>Provides rating lookup by agentId (ordered by recency), user-filtered lookup,
 * average score aggregation and count queries.</p>
 */
@Repository
public interface AgentRatingRepository extends JpaRepository<AgentRating, Long> {

    List<AgentRating> findByAgentIdOrderByCreatedAtDesc(String agentId);

    List<AgentRating> findByAgentIdAndUserId(String agentId, String userId);

    long countByAgentId(String agentId);

    @Query("SELECT COALESCE(AVG(r.score), 0.0) FROM AgentRating r WHERE r.agentId = :agentId")
    double avgScoreByAgentId(@Param("agentId") String agentId);
}
