package com.agent.modelgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Model Gateway Spring Boot entrypoint (doc 02-api §5, PRD §二(二) model gateway).
 *
 * <p>Skeleton stage: interfaces + in-memory Impl + tests. gRPC server / JPA / Redis / WebClient
 * integration deferred to Plan 07 T4-T14 deepening.</p>
 */
@SpringBootApplication
public class ModelGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelGatewayApplication.class, args);
    }
}
