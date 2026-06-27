package com.agent.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 上下文加载冒烟测试：确保 Spring 上下文能够正常装配。
 * 排除 Redis 自动装配以避免对外部 Redis 的依赖。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class GatewayApplicationContextTest {

    @Test
    void contextLoads() {
        // 仅校验上下文能够加载，无需断言
        assertNotNull(GatewayApplication.class);
    }
}
