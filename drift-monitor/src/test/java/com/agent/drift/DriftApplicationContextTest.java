package com.agent.drift;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring Boot context load test for drift-monitor module.
 *
 * <p>Verifies that the application context starts successfully with H2 in-memory database.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("DriftMonitor Application Context")
class DriftApplicationContextTest {

    @Test
    @DisplayName("Should_LoadContext_When_ApplicationStarts: context loads successfully")
    void should_LoadContext_When_ApplicationStarts() {
        // If this test passes, the Spring context loaded without errors
    }
}
