package com.agent.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Knowledge service configuration properties (Plan 08 T10).
 *
 * <p>Binds to {@code knowledge.*} prefix in application.yml.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {

    /** Milvus vector store configuration. */
    private Milvus milvus = new Milvus();

    /** Embedding service configuration. */
    private Embedding embedding = new Embedding();

    @Data
    public static class Milvus {
        /** Whether Milvus vector store is enabled. */
        private boolean enabled = false;
        /** Milvus server host. */
        private String host = "localhost";
        /** Milvus server port. */
        private int port = 19530;
    }

    @Data
    public static class Embedding {
        /** Whether HTTP embedding API is enabled. */
        private boolean httpEnabled = false;
        /** Embedding API endpoint URL. */
        private String endpoint = "http://localhost:8094/v1/embeddings";
    }
}
