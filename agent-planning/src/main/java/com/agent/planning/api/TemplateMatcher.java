package com.agent.planning.api;

import com.agent.planning.model.PlanTemplate;
import java.util.List;
import java.util.Optional;

/**
 * Template matcher (doc 03-task-engine §8.2 PlanMode.TEMPLATE).
 *
 * <p>Matches pre-defined templates by scenario tags + success rate filtering.
 * Skeleton stage: in-memory tag intersection. Vector similarity deferred to Plan 04.</p>
 */
public interface TemplateMatcher {

    /**
     * Match a template for the given task schema + scenario tags.
     *
     * @param taskSchemaJson task schema JSON (for structural matching, may be null)
     * @param scenarioTags  scenario tags to match against template tags
     * @return matched template (highest success rate), empty if no match
     */
    Optional<PlanTemplate> match(String taskSchemaJson, List<String> scenarioTags);

    /**
     * Register a template for matching.
     */
    void register(PlanTemplate template);
}
