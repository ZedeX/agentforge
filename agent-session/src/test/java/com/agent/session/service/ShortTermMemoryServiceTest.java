package com.agent.session.service;

import com.agent.session.config.ShortTermMemoryProperties;
import com.redis.testcontainers.RedisContainer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ShortTermMemoryServiceTest {

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.2"));

    private StringRedisTemplate redisTemplate;
    private ShortTermMemoryService service;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.afterPropertiesSet();

        ShortTermMemoryProperties props = new ShortTermMemoryProperties();
        props.setKeyPrefix("sm");
        props.setTtlHours(24);
        props.setMaxRecentMessages(20);
        service = new ShortTermMemoryService(redisTemplate, props);
    }

    @Test
    @DisplayName("saveContext 后 loadContext 应返回完整上下文字段")
    void should_SaveAndLoadContext_When_ContextPersisted() {
        ShortTermMemoryService.SessionContext ctx = new ShortTermMemoryService.SessionContext();
        ctx.setSystemPrompt("你是订单助手");
        ctx.setTaskGoal("查询订单");
        ctx.setRecentMessages(List.of(Map.of("role", "user", "content", "你好")));
        ctx.setToolHistory(List.of());
        ctx.setRecalledMemory("无相关记忆");

        service.saveContext("ss_ctx_001", ctx);

        ShortTermMemoryService.SessionContext loaded = service.loadContext("ss_ctx_001");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getSystemPrompt()).isEqualTo("你是订单助手");
        assertThat(loaded.getTaskGoal()).isEqualTo("查询订单");
        assertThat(loaded.getRecentMessages()).hasSize(1);
        assertThat(loaded.getRecalledMemory()).isEqualTo("无相关记忆");
    }

    @Test
    @DisplayName("appendMessage 应按调用顺序追加到 recentMessages 列表")
    void should_AppendMessageToRecentList_When_MessagesAppended() {
        ShortTermMemoryService.SessionContext ctx = new ShortTermMemoryService.SessionContext();
        ctx.setSystemPrompt("sys");
        ctx.setRecentMessages(List.of());
        service.saveContext("ss_append_001", ctx);

        service.appendMessage("ss_append_001", Map.of("role", "user", "content", "第一句"));
        service.appendMessage("ss_append_001", Map.of("role", "assistant", "content", "你好"));

        ShortTermMemoryService.SessionContext loaded = service.loadContext("ss_append_001");
        assertThat(loaded.getRecentMessages()).hasSize(2);
        assertThat(loaded.getRecentMessages().get(0).get("content")).isEqualTo("第一句");
    }

    @Test
    @DisplayName("追加消息数超过 maxRecentMessages 时应剔除最早条目")
    void should_RespectMaxRecentMessagesLimit_When_ExceedingLimit() {
        ShortTermMemoryService.SessionContext ctx = new ShortTermMemoryService.SessionContext();
        ctx.setRecentMessages(List.of());
        service.saveContext("ss_max_001", ctx);

        for (int i = 1; i <= 25; i++) {
            service.appendMessage("ss_max_001", Map.of("seq", i));
        }

        ShortTermMemoryService.SessionContext loaded = service.loadContext("ss_max_001");
        assertThat(loaded.getRecentMessages()).hasSize(20);
        assertThat(loaded.getRecentMessages().get(0).get("seq")).isEqualTo(6);
    }

    @Test
    @DisplayName("clearContext 后再 loadContext 应返回 null")
    void should_ClearContext_When_ClearCalled() {
        service.saveContext("ss_clear_001", new ShortTermMemoryService.SessionContext());
        assertThat(service.loadContext("ss_clear_001")).isNotNull();

        service.clearContext("ss_clear_001");

        assertThat(service.loadContext("ss_clear_001")).isNull();
    }

    @Test
    @DisplayName("TTL 到期后 loadContext 应返回 null")
    void should_ExpireContextAfterTtl_When_TtlReached() {
        ShortTermMemoryProperties props = new ShortTermMemoryProperties();
        props.setKeyPrefix("sm");
        props.setTtlHours(0);
        props.setMaxRecentMessages(20);
        // 使用一个极短 TTL（1 秒）的 service 实例
        ShortTermMemoryService shortTtlService = new ShortTermMemoryService(redisTemplate, props) {
            @Override
            protected Duration computeTtl() {
                return Duration.ofSeconds(1);
            }
        };

        shortTtlService.saveContext("ss_expire_001", new ShortTermMemoryService.SessionContext());
        assertThat(shortTtlService.loadContext("ss_expire_001")).isNotNull();

        // FN-010 整改：原 Thread.sleep(1500) 替换为 Awaitility 轮询条件，
        // TTL 一旦失效立即返回，最坏情况等待 3 秒（含 Redis 后台清理延迟）。
        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(shortTtlService.loadContext("ss_expire_001")).isNull());
    }
}
