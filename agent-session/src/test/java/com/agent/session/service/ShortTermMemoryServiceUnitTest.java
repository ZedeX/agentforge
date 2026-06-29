package com.agent.session.service;

import com.agent.session.config.ShortTermMemoryProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShortTermMemoryService Mock 单元测试。
 *
 * <p>不依赖 Docker / Testcontainers Redis：使用 Mockito mock {@link StringRedisTemplate}
 * 与 {@link HashOperations}，覆盖 saveContext / loadContext / appendMessage /
 * clearContext / computeTtl / parseList 路径。</p>
 *
 * <p>对应原 {@code ShortTermMemoryServiceTest}（依赖 Testcontainers Redis，被 no-docker profile
 * 排除）的 Mock 替代版本。Properties 使用真实对象（非 mock），便于在用例中切换
 * keyPrefix / ttlHours / maxRecentMessages 配置。</p>
 *
 * <p>P6-3/4/5：方法名统一为 {@code should_Xxx_When_Yyy}；JUnit 断言替换为 AssertJ；补充中文 @DisplayName。</p>
 */
class ShortTermMemoryServiceUnitTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOps;
    private ShortTermMemoryProperties properties;
    private ShortTermMemoryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        properties = new ShortTermMemoryProperties();
        properties.setKeyPrefix("sm");
        properties.setTtlHours(24);
        properties.setMaxRecentMessages(20);

        service = new ShortTermMemoryService(redisTemplate, properties);
    }

    @Test
    @DisplayName("saveContext 应调用 Redis put 写入 5 个字段并设置 expire")
    void should_CallRedisPutAndExpire_When_SaveContext() {
        ShortTermMemoryService.SessionContext ctx = new ShortTermMemoryService.SessionContext();
        ctx.setSystemPrompt("sys");
        ctx.setTaskGoal("goal");
        ctx.setRecentMessages(List.of(Map.of("role", "user", "content", "hi")));
        ctx.setToolHistory(List.of());
        ctx.setRecalledMemory("memory");

        service.saveContext("session_001", ctx);

        // 5 个字段：systemPrompt / taskGoal / recentMessages / toolHistory / recalledMemory
        verify(hashOps, times(5)).put(anyString(), any(), any());
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("saveContext 在所有字段为 null 时应使用 nullSafe 与 List.of() 兜底")
    void should_HandleNullFields_When_SaveContextWithAllNullFields() {
        ShortTermMemoryService.SessionContext ctx = new ShortTermMemoryService.SessionContext();
        // 所有字段保持 null；nullSafe 与 List.of() 兜底

        service.saveContext("session_001", ctx);

        verify(hashOps, times(5)).put(anyString(), any(), any());
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis put 失败时 saveContext 应抛 IllegalStateException")
    void should_ThrowIllegalState_When_RedisFailsOnSaveContext() {
        // put() 返回 void，必须用 doThrow().when() 语法
        doThrow(new RuntimeException("redis down"))
                .when(hashOps).put(anyString(), any(), any());

        ShortTermMemoryService.SessionContext ctx = new ShortTermMemoryService.SessionContext();
        ctx.setSystemPrompt("sys");

        assertThatThrownBy(() -> service.saveContext("session_001", ctx))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("loadContext 在 key 不存在时应返回 null")
    void should_ReturnNull_When_KeyNotExists() {
        when(hashOps.entries(anyString())).thenReturn(new HashMap<>());

        ShortTermMemoryService.SessionContext ctx = service.loadContext("session_001");
        assertThat(ctx).isNull();
    }

    @Test
    @DisplayName("loadContext 在 key 存在时应返回反序列化后的完整上下文")
    void should_ReturnContext_When_KeyExists() {
        Map<Object, Object> entries = new HashMap<>();
        entries.put("systemPrompt", "sys");
        entries.put("taskGoal", "goal");
        entries.put("recentMessages", "[{\"role\":\"user\",\"content\":\"hi\"}]");
        entries.put("toolHistory", "[]");
        entries.put("recalledMemory", "memory");
        when(hashOps.entries(anyString())).thenReturn(entries);

        ShortTermMemoryService.SessionContext ctx = service.loadContext("session_001");

        assertThat(ctx).isNotNull();
        assertThat(ctx.getSystemPrompt()).isEqualTo("sys");
        assertThat(ctx.getTaskGoal()).isEqualTo("goal");
        assertThat(ctx.getRecentMessages()).hasSize(1);
        assertThat(ctx.getRecentMessages().get(0).get("content")).isEqualTo("hi");
        assertThat(ctx.getToolHistory()).isEmpty();
        assertThat(ctx.getRecalledMemory()).isEqualTo("memory");
    }

    @Test
    @DisplayName("appendMessage 应在已存在列表基础上追加新消息")
    void should_AddToExistingList_When_AppendMessage() throws Exception {
        when(hashOps.get(anyString(), eq("recentMessages")))
                .thenReturn("[{\"role\":\"user\",\"content\":\"hi\"}]");

        service.appendMessage("session_001", Map.of("role", "assistant", "content", "reply"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(hashOps).put(anyString(), eq("recentMessages"), captor.capture());

        List<Map<String, Object>> saved = new ObjectMapper().readValue(
                captor.getValue(), new TypeReference<List<Map<String, Object>>>() {});
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).get("role")).isEqualTo("user");
        assertThat(saved.get(1).get("role")).isEqualTo("assistant");
    }

    @Test
    @DisplayName("appendMessage 在无既有消息时应以空列表起始并追加新消息")
    void should_StartWithEmptyList_When_NoExistingMessages() throws Exception {
        when(hashOps.get(anyString(), eq("recentMessages"))).thenReturn(null);

        service.appendMessage("session_001", Map.of("role", "user", "content", "first"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(hashOps).put(anyString(), eq("recentMessages"), captor.capture());

        List<Map<String, Object>> saved = new ObjectMapper().readValue(
                captor.getValue(), new TypeReference<List<Map<String, Object>>>() {});
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).get("content")).isEqualTo("first");
    }

    @Test
    @DisplayName("appendMessage 在超过 maxRecent 时应剔除最早条目")
    void should_EvictOldest_When_ExceedingMaxRecent() throws Exception {
        properties.setMaxRecentMessages(2);
        when(hashOps.get(anyString(), eq("recentMessages")))
                .thenReturn("[{\"seq\":1},{\"seq\":2}]");

        service.appendMessage("session_001", Map.of("seq", 3));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(hashOps).put(anyString(), eq("recentMessages"), captor.capture());

        List<Map<String, Object>> saved = new ObjectMapper().readValue(
                captor.getValue(), new TypeReference<List<Map<String, Object>>>() {});
        assertThat(saved).hasSize(2);
        // 最早一条（seq=1）应被剔除
        assertThat(((Number) saved.get(0).get("seq")).intValue()).isEqualTo(2);
        assertThat(((Number) saved.get(1).get("seq")).intValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("Redis get 失败时 appendMessage 应抛 IllegalStateException")
    void should_ThrowIllegalState_When_RedisFailsOnAppendMessage() {
        when(hashOps.get(anyString(), eq("recentMessages"))).thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.appendMessage("session_001", Map.of("role", "user")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("clearContext 应调用 Redis delete 删除对应 key")
    void should_CallRedisDelete_When_ClearContext() {
        service.clearContext("session_001");
        verify(redisTemplate).delete(eq("sm:session_001:ctx"));
    }

    @Test
    @DisplayName("computeTtl 在 ttlHours<=0 时应返回默认 24 小时")
    void should_Return24Hours_When_TtlHoursLe0() {
        properties.setTtlHours(0);
        Duration ttl = service.computeTtl();
        assertThat(ttl).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("computeTtl 在 ttlHours>0 时应返回配置的小时数")
    void should_ReturnConfiguredHours_When_TtlHoursGt0() {
        properties.setTtlHours(48);
        Duration ttl = service.computeTtl();
        assertThat(ttl).isEqualTo(Duration.ofHours(48));
    }

    @Test
    @DisplayName("parseList 在 recentMessages 字段缺失时应返回空列表")
    void should_ReturnEmptyList_When_JsonIsNull() {
        // entries 中不含 recentMessages -> get("recentMessages") 返回 null
        Map<Object, Object> entries = new HashMap<>();
        entries.put("systemPrompt", "sys");
        when(hashOps.entries(anyString())).thenReturn(entries);

        ShortTermMemoryService.SessionContext ctx = service.loadContext("session_001");
        assertThat(ctx).isNotNull();
        assertThat(ctx.getRecentMessages()).isNotNull();
        assertThat(ctx.getRecentMessages()).isEmpty();
    }

    @Test
    @DisplayName("parseList 在 recentMessages 为空白字符串时应返回空列表")
    void should_ReturnEmptyList_When_JsonIsBlank() {
        Map<Object, Object> entries = new HashMap<>();
        entries.put("systemPrompt", "sys");
        entries.put("recentMessages", "   ");
        when(hashOps.entries(anyString())).thenReturn(entries);

        ShortTermMemoryService.SessionContext ctx = service.loadContext("session_001");
        assertThat(ctx).isNotNull();
        assertThat(ctx.getRecentMessages()).isEmpty();
    }

    @Test
    @DisplayName("parseList 在 recentMessages 为非法 JSON 时应返回空列表")
    void should_ReturnEmptyList_When_JsonIsInvalid() {
        Map<Object, Object> entries = new HashMap<>();
        entries.put("systemPrompt", "sys");
        entries.put("recentMessages", "invalid json");
        when(hashOps.entries(anyString())).thenReturn(entries);

        ShortTermMemoryService.SessionContext ctx = service.loadContext("session_001");
        assertThat(ctx).isNotNull();
        assertThat(ctx.getRecentMessages()).isEmpty();
    }
}
