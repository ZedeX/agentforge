package com.agent.planning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Spring Boot context load test for agent-planning.
 * Verifies the application context starts successfully with H2 and gRPC configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("PlanningApplication context load test")
class PlanningApplicationContextTest {

    @Test
    @DisplayName("Should_LoadApplicationContext_When_UsingTestProfile: context loads with H2 + gRPC disabled")
    void should_LoadApplicationContext() {
        // If this test passes, the Spring context loaded successfully
        assertThatNoException().isThrownBy(() -> {
            // Context already loaded by @SpringBootTest
        });
    }
}
