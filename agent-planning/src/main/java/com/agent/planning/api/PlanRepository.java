package com.agent.planning.api;

import com.agent.planning.model.Plan;
import java.util.Optional;
import java.util.List;

/**
 * Plan repository (doc 03-task-engine §8.2.1 plan storage).
 *
 * <p>Skeleton stage: in-memory ConcurrentHashMap. JPA + MySQL deferred to Plan 04 deepening.</p>
 */
public interface PlanRepository {

    /**
     * Save or update a plan (upsert by planId).
     *
     * @param plan plan to save
     * @return saved plan (with id assigned if new)
     */
    Plan save(Plan plan);

    /**
     * Find a plan by its planId.
     */
    Optional<Plan> findById(String planId);

    /**
     * Find all plans for a given taskId.
     */
    List<Plan> findByTaskId(String taskId);

    /**
     * Update plan status.
     *
     * @param planId plan identifier
     * @param newStatus new status code
     * @return true if updated, false if plan not found
     */
    boolean updateStatus(String planId, String newStatus);

    /**
     * Delete a plan by planId.
     */
    boolean delete(String planId);
}
