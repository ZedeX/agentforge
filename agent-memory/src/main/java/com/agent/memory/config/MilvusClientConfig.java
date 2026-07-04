package com.agent.memory.config;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus client configuration (Plan 03 T6).
 *
 * <p>Creates {@link MilvusClientV2} bean when {@code memory.milvus.enabled=true}.
 * In-memory fallback is used when disabled (default).</p>
 *
 * @see MemoryProperties.Milvus
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "memory.milvus.enabled", havingValue = "true")
@EnableConfigurationProperties(MemoryProperties.class)
public class MilvusClientConfig {

    @Bean
    public MilvusClientV2 milvusClient(MemoryProperties properties) {
        MemoryProperties.Milvus milvusProps = properties.getMilvus();
        String uri = String.format("http://%s:%d", milvusProps.getHost(), milvusProps.getPort());
        log.info("Connecting to Milvus at {}", uri);

        ConnectConfig config = ConnectConfig.builder()
                .uri(uri)
                .build();

        MilvusClientV2 client = new MilvusClientV2(config);
        log.info("Milvus client connected successfully");
        return client;
    }
}
