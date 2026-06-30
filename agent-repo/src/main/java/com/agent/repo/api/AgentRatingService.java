package com.agent.repo.api;

import com.agent.repo.model.AgentRating;

import java.util.List;

/**
 * Agent rating service (doc 06-agent-repo §3.2).
 *
 * <p>Submits / queries user ratings for agents. Maintains per-agent average score cache.</p>
 */
public interface AgentRatingService {

    /**
     * Submit a rating for an agent.
     *
     * <p>Score must be in [1,5]. Out-of-range scores are clamped to the nearest bound.</p>
     *
     * @param rating rating to submit
     * @return saved rating (with assigned id / createdAt)
     */
    AgentRating submit(AgentRating rating);

    /**
     * List all ratings for an agent, oldest first.
     */
    List<AgentRating> findByAgent(String agentId);

    /**
     * Get average score for an agent.
     *
     * @return average score in [1.0, 5.0], or 0.0 if no ratings
     */
    double getAverageScore(String agentId);

    /**
     * Count ratings for an agent.
     */
    int countRatings(String agentId);
}
