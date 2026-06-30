package com.agent.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Knowledge Service Spring Boot entrypoint (doc 00-overview §3.1 port 8098, PRD §二(二) knowledge base).
 *
 * <p>Skeleton stage: interfaces + in-memory Impl + tests. JPA / Milvus / gRPC server integration
 * deferred to Plan 08 T7-T12 deepening.</p>
 */
@SpringBootApplication
public class KnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
    }
}
