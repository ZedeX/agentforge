package com.agent.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring context load test for agent-observability module.
 */
@SpringBootTest
@ActiveProfiles("test")
class ObservabilityApplicationContextTest {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts successfully
        // with all beans (gRPC service, mappers, repositories, properties) wired correctly.
        assertThat(true).isTrue();
    }
}
