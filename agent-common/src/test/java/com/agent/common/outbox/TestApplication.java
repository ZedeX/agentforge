package com.agent.common.outbox;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Minimal Spring Boot application for @DataJpaTest slice tests in agent-common.
 *
 * <p>agent-common is a library module without its own Application class.
 * This inner class provides the minimal context for JPA repository tests.</p>
 */
@SpringBootApplication(scanBasePackages = "com.agent.common.outbox")
class TestApplication {
    static void main(String[] args) {
        new SpringApplicationBuilder(TestApplication.class).run(args);
    }
}
