package com.agent.drift.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * drift-monitor configuration properties (aligned doc 11 F11 drift detection).
 *
 * <p>Prefix {@code drift}, maps to {@code drift.*} in application.yml.
 * Sub-configurations as static inner classes.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "drift")
public class DriftMonitorProperties {

    /** Drift detection thresholds. */
    private Detection detection = new Detection();

    /** Drift correction config. */
    private Correction correction = new Correction();

    @Getter
    @Setter
    public static class Detection {
        /** Threshold for slight drift (default 0.3). */
        private double thresholdSlight = 0.3;
        /** Threshold for moderate drift (default 0.6). */
        private double thresholdModerate = 0.6;
        /** Threshold for severe drift (default 0.8). */
        private double thresholdSevere = 0.8;
    }

    @Getter
    @Setter
    public static class Correction {
        /** Whether to auto-reset baseline on severe drift (default true). */
        private boolean autoReset = true;
    }
}
