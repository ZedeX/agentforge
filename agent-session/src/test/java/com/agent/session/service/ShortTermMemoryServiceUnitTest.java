package com.agent.session.service;

import com.agent.session.config.ShortTermMemoryProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void saveContext_shouldCallRedisPutAndExpire() {
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
    void saveContext_shouldHandleNullFields() {
        ShortTermMemoryService.SessionContext ctx = new ShortTermMemoryService.SessionContext();
        // 所有字段保持 null；nullSafe 与 List.of() 兜底

        service.saveContext("session_001", ctx);

        verify(hashOps, times(5)).put(anyString(), any(), any());
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    void saveContext_shouldThrowIllegalState_whenRedisFails() {
        // put() 返回 void，必须用 doThrow().when() 语法
        doThrow(new RuntimeException("redis down"))
                .when(hashOps).put(anyString(), any(), any());

        ShortTermMemoryService.SessionContext ctx = new ShortTermMemoryService.SessionContext();
        ctx.setSystemPrompt("sys");

        assertThrows(IllegalStateException.class,
                () -> service.saveContext("session_001", ctx));
    }

    @Test
    void loadContext_shouldReturnNull_whenKeyNotExists() {
        when(hashOps.entries(anyString())).thenReturn(new HashMap<>());

        ShortTermMemoryService.SessionContext ctx = service.loadContext("session_001");
        assertNull(ctx);
    }

    @Test
    void loadContext_shouldReturnContext_whenKeyExists() {
        Map<Object, Object> entries = new HashMap<>();
        entries.put("systemPrompt", "sys");
        entries.put("taskGoal", "goal");
        entries.put("recentMessages", "[{\"role\":\"user\",\"content\":\"hi\"}]");
        entries.put("toolHistory", "[]");
        entries.put("recalledMemory", "memory");
        when(hashOps.entries(anyString())).thenReturn(entries);

        ShortTermMemoryService.SessionContext ctx = service.loadContext("session_001");

        assertNotNull(ctx);
        assertEquals("sys", ctx.getSystemPrompt());
        assertEquals("goal", ctx.getTaskGoal());
        assertEquals(1, ctx.getRecentMessages().size());
        assertEquals("hi", ctx.getRecentMessages().get(0).get("content"));
        assertTrue(ctx.getToolHistory().isEmpty());
        assertEquals("memory", ctx.getRecalledMemory());
    }

    @Test
    void appendMessage_shouldAddToExistingList() throws Exception {
        when(hashOps.get(anyString(), eq("recentMessages")))
                .thenReturn("[{\"role\":\"user\",\"content\":\"hi\"}]");

        service.appendMessage("session_001", Map.of("role", "assistant", "content", "reply"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(hashOps).put(anyString(), eq("recentMessages"), captor.capture());

        List<Map<String, Object>> saved = new ObjectMapper().readValue(
                captor.getValue(), new TypeReference<List<Map<String, Object>>>() {});
        assertEquals(2, saved.size());
        assertEquals("user", saved.get(0).get("role"));
        assertEquals("assistant", saved.get(1).get("role"));
    }

    @Test
    void appendMessage_shouldStartWithEmptyList_whenNoExistingMessages() throws Exception {
        when(hashOps.get(anyString(), eq("recentMessages"))).thenReturn(null);

        service.appendMessage("session_001", Map.of("role", "user", "content", "first"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(hashOps).put(anyString(), eq("recentMessages"), captor.capture());

        List<Map<String, Object>> saved = new ObjectMapper().readValue(
                captor.getValue(), new TypeReference<List<Map<String, Object>>>() {});
        assertEquals(1, saved.size());
        assertEquals("first", saved.get(0).get("content"));
    }

    @Test
    void appendMessage_shouldEvictOldest_whenExceedingMaxRecent() throws Exception {
        properties.setMaxRecentMessages(2);
        when(hashOps.get(anyString(), eq("recentMessages")))
                .thenReturn("[{\"seq\":1},{\"seq\":2}]");

        service.appendMessage("session_001", Map.of("seq", 3));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(hashOps).put(anyString(), eq("recentMessages"), captor.capture());

        List<Map<String, Object>> saved = new ObjectMapper().readValue(
                captor.getValue(), new TypeReference<List<Map<String, Object>>>() {});
        assertEquals(2, saved.size());
        // 最早一条（seq=1）应被剔除
        assertEquals(2, ((Number) saved.get(0).get("seq")).intValue());
        assertEquals(3, ((Number) saved.get(1).get("seq")).intValue());
    }

    @Test
    void appendMessage_shouldThrowIllegalState_whenRedisFails() {
        when(hashOps.get(anyString(), eq("recentMessages"))).thenThrow(new RuntimeException("redis down"));

        assertThrows(IllegalStateException.class,
                () -> service.appendMessage("session_001", Map.of("role", "user")));
    }

    @Test
    void clearContext_shouldCallRedisDelete() {
        service.clearContext("session_001");
        verify(redisTemplate).delete(eq("sm:session_001:ctx"));
    }

    @Test
    void computeTtl_shouldReturn24Hours_whenTtlHoursLe0() {
        properties.setTtlHours(0);
        Duration ttl = service.computeTtl();
        assertEquals(Duration.ofHours(24), ttl);
    }

    @Test
    void computeTtl_shouldReturnConfiguredHours_whenTtlHoursGt0() {
        properties.setTtlHours(48);
        Duration ttl = service.computeTtl();
        assertEquals(Duration.ofHours(48), ttl);
    }

    @Test
    void parseList_shouldReturnEmptyList_whenJsonIsNull() {
        // entries 中不含 recentMessages -> get("recentMessages") 返回 null
        Map<Object, Object> entries = new HashMap<>();
        entries.put("systemPrompt", "sys");
        when(hashOps.entries(anyString())).thenReturn(entries);

        ShortTermMemoryService.SessionContext ctx = service.loadContext("session_001");
        assertNotNull(ctx);
        assertNotNull(ctx.getRecentMessages());
        assertTrue(ctx.getRecentMessages().isEmpty());
    }

    @Test
    void parseList_shouldReturnEmptyList_whenJsonIsBlank() {
        Map<Object, Object> entries = new HashMap<>();
        entries.put("systemPrompt", "sys");
        entries.put("recentMessages", "   ");
        when(hashOps.entries(anyString())).thenReturn(entries);

        ShortTermMemoryService.SessionContext ctx = service.loadContext("session_001");
        assertNotNull(ctx);
        assertTrue(ctx.getRecentMessages().isEmpty());
    }

    @Test
    void parseList_shouldReturnEmptyList_whenJsonIsInvalid() {
        Map<Object, Object> entries = new HashMap<>();
        entries.put("systemPrompt", "sys");
        entries.put("recentMessages", "invalid json");
        when(hashOps.entries(anyString())).thenReturn(entries);

        ShortTermMemoryService.SessionContext ctx = service.loadContext("session_001");
        assertNotNull(ctx);
        assertTrue(ctx.getRecentMessages().isEmpty());
    }
}
