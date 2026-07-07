package com.agent.planning.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * agent-planning configuration properties.
 *
 * <p>Prefix {@code planning}, maps application.yml {@code planning.*} items.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "planning")
public class PlanningProperties {

    /** Template matching configuration. */
    private Template template = new Template();

    /** Replan strategy configuration. */
    private Replan replan = new Replan();

    @Getter
    @Setter
    public static class Template {
        /** Whether template matching is enabled. */
        private boolean enabled = true;
        /** Minimum success rate for a template to be considered. */
        private double minSuccessRate = 0.6;
    }

    @Getter
    @Setter
    public static class Replan {
        /** Maximum replan attempts before requiring manual intervention. */
        private int maxCount = 5;
        /** Replan count threshold that triggers MANUAL mode. */
        private int manualThreshold = 3;
    }
}
