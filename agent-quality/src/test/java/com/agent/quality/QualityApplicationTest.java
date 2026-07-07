package com.agent.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Spring 上下文加载测试（验证 QualityApplication 能正常启动）。
 */
@SpringBootTest(classes = QualityApplication.class)
@ActiveProfiles("test")
class QualityApplicationTest {

    @Test
    @DisplayName("Spring 上下文加载成功: 不抛异常")
    void should_LoadContext_When_ApplicationStarts() {
        assertThatNoException();
    }
}
