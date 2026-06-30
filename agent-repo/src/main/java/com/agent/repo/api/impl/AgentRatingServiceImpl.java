package com.agent.repo.api.impl;

import com.agent.repo.api.AgentRatingService;
import com.agent.repo.model.AgentRating;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory agent rating service (doc 06-agent-repo §3.2).
 *
 * <p>Skeleton stage: maintains per-agentId ratings list in ConcurrentHashMap. Score is clamped
 * to [1,5] on submit. Average score is computed on demand (no cache) for simplicity.</p>
 */
@Component
public class AgentRatingServiceImpl implements AgentRatingService {

    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 5;

    private final Map<String, List<AgentRating>> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    @Override
    public AgentRating submit(AgentRating rating) {
        if (rating == null || rating.getAgentId() == null) {
            throw new IllegalArgumentException("rating or agentId must not be null");
        }
        // Clamp score to [1,5]
        int clamped = Math.max(MIN_SCORE, Math.min(MAX_SCORE, rating.getScore()));
        rating.setScore(clamped);
        rating.setId(idSeq.incrementAndGet());
        rating.setCreatedAt(System.currentTimeMillis());
        store.computeIfAbsent(rating.getAgentId(), k -> new ArrayList<>()).add(rating);
        return rating;
    }

    @Override
    public List<AgentRating> findByAgent(String agentId) {
        if (agentId == null) {
            return new ArrayList<>();
        }
        List<AgentRating> ratings = store.get(agentId);
        return ratings != null ? new ArrayList<>(ratings) : new ArrayList<>();
    }

    @Override
    public double getAverageScore(String agentId) {
        if (agentId == null) {
            return 0.0;
        }
        List<AgentRating> ratings = store.get(agentId);
        if (ratings == null || ratings.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (AgentRating r : ratings) {
            sum += r.getScore();
        }
        return sum / ratings.size();
    }

    @Override
    public int countRatings(String agentId) {
        if (agentId == null) {
            return 0;
        }
        List<AgentRating> ratings = store.get(agentId);
        return ratings != null ? ratings.size() : 0;
    }
}
