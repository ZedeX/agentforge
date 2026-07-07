package com.agent.tool.engine.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * S-04: Enables JPA entity scanning and repository detection for the
 * outbox framework in {@code com.agent.common.outbox}.
 *
 * <p>This separate @Configuration class is needed because
 * {@code @SpringBootApplication} only scans {@code com.agent.tool.engine}
 * and its sub-packages. The outbox entity and repository live in
 * {@code com.agent.common.outbox} which is outside the default scan scope.</p>
 *
 * <p>Both packages are listed to ensure the tool-engine's own entities
 * and repositories continue to work alongside the outbox ones.</p>
 */
@Configuration
@EntityScan(basePackages = {"com.agent.tool.engine", "com.agent.common.outbox"})
@EnableJpaRepositories(basePackages = {"com.agent.tool.engine", "com.agent.common.outbox"})
public class OutboxJpaConfig {
}
