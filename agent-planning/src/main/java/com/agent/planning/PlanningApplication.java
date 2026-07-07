package com.agent.planning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Agent Planning Spring Boot entrypoint (doc 03-task-engine §8.2, PRD §二(三) planning).
 *
 * <p>HTTP 8086 / gRPC 9086 (aligned with doc 00-overview §3.1).
 * Configuration see application.yml.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.agent.planning.config")
public class PlanningApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlanningApplication.class, args);
    }
}
