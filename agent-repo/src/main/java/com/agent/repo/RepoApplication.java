package com.agent.repo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Agent Repo Spring Boot entrypoint (doc 00-overview §3.1, PRD §二(二) agent-repo, port 8096).
 *
 * <p>Skeleton stage: interfaces + in-memory Impl + tests. JPA / gRPC server / Flyway / Milvus
 * integration deferred to Plan 08 T2-T6 deepening.</p>
 */
@SpringBootApplication
public class RepoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepoApplication.class, args);
    }
}
