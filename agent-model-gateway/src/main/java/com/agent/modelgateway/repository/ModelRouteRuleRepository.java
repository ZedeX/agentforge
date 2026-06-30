package com.agent.modelgateway.repository;

import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.ModelRouteRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ModelRouteRule} (Plan 07 T3).
 *
 * <p>Supports scene-based rule lookup ordered by priority (lower value = higher precedence).</p>
 */
@Repository
public interface ModelRouteRuleRepository extends JpaRepository<ModelRouteRule, Long> {

    /** Find enabled rules for a given scene, ordered by priority ascending (lowest first). */
    List<ModelRouteRule> findBySceneAndEnabledTrueOrderByPriorityAsc(Scene scene);

    /** Find all enabled rules (for full rule set reload / cache warmup). */
    List<ModelRouteRule> findByEnabledTrueOrderByPriorityAsc();

    /** Count enabled rules for a scene (health check / config validation). */
    long countBySceneAndEnabledTrue(Scene scene);
}
