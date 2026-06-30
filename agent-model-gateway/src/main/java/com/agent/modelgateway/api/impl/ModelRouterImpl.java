package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.api.ModelRouter;
import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.ModelRouteRule;
import com.agent.modelgateway.model.RouteResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory model router (doc 02-api §3).
 *
 * <p>Skeleton stage: maintains route rules in a CopyOnWriteArrayList. Matching: scene + priority
 * (lower priority value = higher precedence). Falls back to GENERIC if no scene-specific rule.
 * JPA-backed routing deferred to Plan 07 T3.</p>
 */
@Component
public class ModelRouterImpl implements ModelRouter {

    private final List<ModelRouteRule> rules = new CopyOnWriteArrayList<>();

    /** Default fallback provider codes per scene (used when no rule registered). */
    private static final String DEFAULT_INTENT_PRIMARY = "openai-mini";
    private static final String DEFAULT_INTENT_FALLBACK = "qwen-turbo";
    private static final String DEFAULT_AUDIT_PRIMARY = "anthropic";
    private static final String DEFAULT_AUDIT_FALLBACK = "openai";
    private static final String DEFAULT_GENERIC_PRIMARY = "openai";
    private static final String DEFAULT_GENERIC_FALLBACK = "anthropic";

    public ModelRouterImpl() {
        // Seed default rules
        addRule(new ModelRouteRule(Scene.INTENT, 1, DEFAULT_INTENT_PRIMARY, DEFAULT_INTENT_FALLBACK));
        addRule(new ModelRouteRule(Scene.AUDIT, 1, DEFAULT_AUDIT_PRIMARY, DEFAULT_AUDIT_FALLBACK));
        addRule(new ModelRouteRule(Scene.GENERIC, 10, DEFAULT_GENERIC_PRIMARY, DEFAULT_GENERIC_FALLBACK));
    }

    public void addRule(ModelRouteRule rule) {
        if (rule == null || rule.getScene() == null) {
            return;
        }
        rules.add(rule);
    }

    @Override
    public RouteResult route(Scene scene, String preferredModel) {
        if (scene == null) {
            scene = Scene.GENERIC;
        }
        // Preferred model overrides routing
        if (preferredModel != null && !preferredModel.isEmpty()) {
            return new RouteResult(preferredModel, null, true);
        }
        // Find matching rule with lowest priority value
        ModelRouteRule best = null;
        for (ModelRouteRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            if (rule.getScene() != scene) {
                continue;
            }
            if (best == null || rule.getPriority() < best.getPriority()) {
                best = rule;
            }
        }
        if (best == null) {
            // Fallback to GENERIC rules
            for (ModelRouteRule rule : rules) {
                if (rule.isEnabled() && rule.getScene() == Scene.GENERIC) {
                    if (best == null || rule.getPriority() < best.getPriority()) {
                        best = rule;
                    }
                }
            }
        }
        if (best == null) {
            // Ultimate default
            return new RouteResult(DEFAULT_GENERIC_PRIMARY, DEFAULT_GENERIC_FALLBACK, true);
        }
        return new RouteResult(best.getPrimaryProviderCode(), best.getFallbackProviderCode(), true);
    }
}
