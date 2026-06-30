package com.agent.planning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Agent Planning Spring Boot entrypoint (doc 03-task-engine §8.2, PRD §二(三) planning).
 *
 * <p>Skeleton stage: interfaces + in-memory Impl + tests. gRPC server / JPA / Redis / AI planner
 * integration deferred to Plan 04 deepening.</p>
 */
@SpringBootApplication
public class PlanningApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlanningApplication.class, args);
    }
}
