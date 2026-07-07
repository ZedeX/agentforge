package com.agent.riskcontrol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Spring context loads test for agent-risk-control.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("RiskControl Spring Context")
class RiskControlApplicationContextTest {

    @Test
    @DisplayName("Should_LoadContext_When_ApplicationStarts: Spring context loads successfully")
    void should_LoadContext_When_ApplicationStarts() {
        assertThatNoException();
    }
}
