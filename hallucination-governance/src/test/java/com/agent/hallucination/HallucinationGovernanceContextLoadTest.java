package com.agent.hallucination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * HallucinationGovernance Spring 上下文加载测试。
 *
 * <p>验证 ApplicationContext 能正常启动，所有 Bean 注入无循环依赖。</p>
 */
@SpringBootTest(classes = HallucinationGovernanceApplication.class)
@ActiveProfiles("test")
@DisplayName("HallucinationGovernance 上下文加载测试")
class HallucinationGovernanceContextLoadTest {

    @Test
    @DisplayName("Should_LoadContext_When_ApplicationStarts: Spring 上下文加载无异常")
    void should_LoadContext_When_ApplicationStarts() {
        assertThatNoException().isThrownBy(() -> {
            // Spring 上下文由 @@SpringBootTest 自动加载
        });
    }
}
