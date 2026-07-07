package com.agent.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Agent Observability service entry point (trace propagation, metrics collection, health aggregation).
 *
 * <p>HTTP 8104 / gRPC 9104.
 * Port configuration in application.yml.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.agent.observability.config")
public class ObservabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservabilityApplication.class, args);
    }
}
