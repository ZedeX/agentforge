package com.agent.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上下文加载冒烟测试：确保 Spring 上下文能够正常装配。
 * 排除 Redis 自动装配以避免对外部 Redis 的依赖。
 *
 * <p>P6-3/4/5：方法名统一为 {@code should_Xxx_When_Yyy}；JUnit 断言替换为 AssertJ；补充中文 @DisplayName。</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class GatewayApplicationContextTest {

    @Test
    @DisplayName("Spring 上下文应能正常加载装配 GatewayApplication")
    void should_LoadApplicationContext_When_ApplicationStarts() {
        // 仅校验上下文能够加载，无需断言
        assertThat(GatewayApplication.class).isNotNull();
    }
}
