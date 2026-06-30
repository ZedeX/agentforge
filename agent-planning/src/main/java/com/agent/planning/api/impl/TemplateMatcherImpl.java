package com.agent.planning.api.impl;

import com.agent.planning.api.TemplateMatcher;
import com.agent.planning.model.PlanTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory template matcher (doc 03-task-engine §8.2 TemplateMatcher).
 *
 * <p>Matching logic:
 * - null/empty scenarioTags → no match (cannot identify scenario)
 * - filter enabled templates
 * - keep templates whose scenarioTags intersect with request tags (≥1 match)
 * - filter by success rate ≥ MIN_SUCCESS_RATE (0.6)
 * - return highest success rate; tie-breaker: highest useCount</p>
 *
 * <p>Skeleton stage: tag intersection. Vector similarity deferred to Plan 04.</p>
 */
@Component
public class TemplateMatcherImpl implements TemplateMatcher {

    private static final double MIN_SUCCESS_RATE = 0.6;

    private final List<PlanTemplate> templates = new CopyOnWriteArrayList<>();

    @Override
    public Optional<PlanTemplate> match(String taskSchemaJson, List<String> scenarioTags) {
        if (scenarioTags == null || scenarioTags.isEmpty()) {
            return Optional.empty();
        }
        List<PlanTemplate> candidates = new ArrayList<>();
        for (PlanTemplate t : templates) {
            if (!t.isEnabled()) {
                continue;
            }
            if (t.getScenarioTags() == null || t.getScenarioTags().isEmpty()) {
                continue;
            }
            if (t.getSuccessRate() < MIN_SUCCESS_RATE) {
                continue;
            }
            // Check tag intersection (case-insensitive)
            boolean intersects = false;
            for (String tag : scenarioTags) {
                if (tag == null) {
                    continue;
                }
                for (String tplTag : t.getScenarioTags()) {
                    if (tplTag != null && tplTag.equalsIgnoreCase(tag)) {
                        intersects = true;
                        break;
                    }
                }
                if (intersects) {
                    break;
                }
            }
            if (intersects) {
                candidates.add(t);
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        // Sort by successRate desc, then useCount desc
        candidates.sort((a, b) -> {
            int cmp = Double.compare(b.getSuccessRate(), a.getSuccessRate());
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(b.getUseCount(), a.getUseCount());
        });
        return Optional.of(candidates.get(0));
    }

    @Override
    public void register(PlanTemplate template) {
        if (template == null || template.getName() == null) {
            return;
        }
        templates.add(template);
    }
}
