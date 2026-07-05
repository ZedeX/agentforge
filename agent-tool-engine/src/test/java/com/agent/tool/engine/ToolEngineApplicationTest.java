package com.agent.tool.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link ToolEngineApplication} Spring Context 加载测试（Plan 05 T1 Red 阶段）。
 *
 * <p>验证 pom.xml 依赖 / application.yml 配置 / 启动类 / 4 个 config bean 全部就绪，
 * Spring Context 能成功加载。Red 阶段预期失败（缺依赖 + 缺启动类 + 缺配置）。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ToolEngineApplicationTest {

    @Test
    @DisplayName("Spring Context 加载成功")
    void contextLoads() {
        // 验证 ToolEngineApplication 启动类 + 全套 config bean 可加载
    }
}
