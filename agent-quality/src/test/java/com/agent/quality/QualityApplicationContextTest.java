package com.agent.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring 上下文加载测试 —— 验证 agent-quality 模块 Spring Boot 上下文可正常启动。
 *
 * <p>使用 H2 内存数据库（application-test.yml），避免依赖外部 MySQL。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Quality Spring Context Load Test")
class QualityApplicationContextTest {

    @Test
    @DisplayName("Should_LoadApplicationContext_When_SpringBootStarts: Spring 上下文加载成功")
    void should_LoadApplicationContext_When_SpringBootStarts() {
        // 上下文加载成功即通过，无需额外断言
    }
}
