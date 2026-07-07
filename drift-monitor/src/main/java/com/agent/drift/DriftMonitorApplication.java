package com.agent.drift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Drift Monitor service startup class (F11 behavior/effect/alignment/memory drift detection).
 *
 * <p>HTTP 8108 / gRPC 9108 (aligned doc 00-overview 3.1).
 * Port configuration in application.yml.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.agent.drift.config")
public class DriftMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriftMonitorApplication.class, args);
    }
}
