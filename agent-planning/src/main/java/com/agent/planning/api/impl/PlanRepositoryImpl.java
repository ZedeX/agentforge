package com.agent.planning.api.impl;

import com.agent.planning.api.PlanRepository;
import com.agent.planning.enums.PlanStatus;
import com.agent.planning.model.Plan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory plan repository (doc 03-task-engine §8.2.1 plan storage).
 *
 * <p>Skeleton stage: ConcurrentHashMap keyed by planId. JPA + MySQL deferred to Plan 04 deepening.</p>
 */
@Component
public class PlanRepositoryImpl implements PlanRepository {

    private final Map<String, Plan> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    @Override
    public Plan save(Plan plan) {
        if (plan == null || plan.getPlanId() == null || plan.getPlanId().isEmpty()) {
            return null;
        }
        if (plan.getId() == null) {
            plan.setId(idGenerator.incrementAndGet());
        }
        plan.setUpdatedAt(System.currentTimeMillis());
        store.put(plan.getPlanId(), plan);
        return plan;
    }

    @Override
    public Optional<Plan> findById(String planId) {
        if (planId == null || planId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(planId));
    }

    @Override
    public List<Plan> findByTaskId(String taskId) {
        List<Plan> results = new ArrayList<>();
        if (taskId == null || taskId.isEmpty()) {
            return results;
        }
        for (Plan plan : store.values()) {
            if (taskId.equals(plan.getTaskId())) {
                results.add(plan);
            }
        }
        return results;
    }

    @Override
    public boolean updateStatus(String planId, String newStatus) {
        if (planId == null || planId.isEmpty() || newStatus == null) {
            return false;
        }
        Plan plan = store.get(planId);
        if (plan == null) {
            return false;
        }
        PlanStatus status = PlanStatus.fromCode(newStatus);
        plan.setStatus(status);
        plan.setUpdatedAt(System.currentTimeMillis());
        return true;
    }

    @Override
    public boolean delete(String planId) {
        if (planId == null || planId.isEmpty()) {
            return false;
        }
        return store.remove(planId) != null;
    }
}
